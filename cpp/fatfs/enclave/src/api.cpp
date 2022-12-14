#include <string.h>
#include <assert.h>
#include <limits>
#include <stdexcept>
#include <string>
#include <vector>
#include <unordered_set>

#include "enclave_shared_data.h"
#include "substrate_jvm.h"
#include "sys_stat.h"
#include "graal_isolate.h"
#include "vm_enclave_layer.h"
#include "conclave-stat.h"
#include "unistd.h"
#include "sgx_tcrypto.h"

#include <jni.h>
#include <jvm_t.h>
#include <dlsym_symbols.h>

#include "disk.hpp"
#include "inmemory_disk.hpp"
#include "persistent_disk.hpp"
#include "fatfs_file_manager.hpp"
#include "fatfs_result.hpp"

static const int kMaxNumFiles = 500000;

//  The size of the filesystems is currently limited to Fat32 max size, which is 2T.
//  Note that this value of 2T comes from the maximum value of the number of sectors
//    that we can specify on 32 bits (4G) times the bytes for each sector (512).
static const unsigned long kMaxInMemorySize = ((unsigned long)UINT_MAX * SECTOR_SIZE);
static const unsigned long kMaxPersistentSize = ((unsigned long)UINT_MAX * SECTOR_SIZE);

static int currentFirstAvailableHandle  = 100000;
static int currentDummyHandle = currentFirstAvailableHandle;
static std::unordered_set<int> dummyHandles;
static std::mutex dummyHandleMutex;
static std::vector<std::shared_ptr<conclave::FatFsFileManager> > filesystems;

static std::string currentPath = "/";
static JavaVM *jvm = NULL;

enum FileSystemType { IN_MEMORY, PERSISTENT };

std::unique_ptr<conclave::FatFsDisk> createDiskHandler(const FileSystemType type,
                                                       const BYTE drive_id,
                                                       const unsigned long size,
                                                       const unsigned char* encryption_key) {
    if (type == FileSystemType::PERSISTENT) {
        return std::unique_ptr<conclave::FatFsDisk>(new conclave::PersistentDisk(drive_id, size, encryption_key));
    } else if (type == FileSystemType::IN_MEMORY) {
        return std::unique_ptr<conclave::FatFsDisk>(new conclave::InMemoryDisk(drive_id, size));
    } else {
        const std::string message = "Developer error, no filesystem type defined";
        throw std::runtime_error(message);
    }   
}


static std::shared_ptr<conclave::FatFsFileManager> createFileSystem(const FileSystemType type,
                                                                    const BYTE drive,
                                                                    const unsigned long size,
                                                                    const unsigned char* encryption_key,
                                                                    const std::string& mount_path) {
    const int first_handle = currentFirstAvailableHandle;
    const int max_handle = currentFirstAvailableHandle + kMaxNumFiles -1;
    auto disk_handler = createDiskHandler(type, drive, size, encryption_key);
    auto filesystem = std::make_shared<conclave::FatFsFileManager>(first_handle,
                                                                   max_handle,
                                                                   encryption_key,
                                                                   mount_path,
                                                                   std::move(disk_handler));
    currentFirstAvailableHandle += kMaxNumFiles;
    currentDummyHandle = currentFirstAvailableHandle;
    return filesystem;
}

// Convert a path from JNI and convert it to a string
static std::string getJniMountPath(JNIEnv* env, jstring& path_in) {
    jboolean is_copy;
    const char* path_str = env->GetStringUTFChars(path_in, &is_copy);
    std::string path = (path_str == NULL) ? std::string() : std::string(path_str);
    env->ReleaseStringUTFChars(path_in, path_str);

    if (path.length() > 0 && path.back() != '/') {
        //  As we use these as mountpoints, our code needs to assume that we have a '/' at the end.
        //    If we do not have it, we add it here.
        //  This is to distinguish properly between /tmp vs /tmptest for example,
        //    which are converted to /tmp/ and /tmptest/ and won't cause comparison issues.
        path += "/";
    }
    return path;
}


// Get the encryption key from JNI and convert it to a string
static bool getJniEncryptionKey(JNIEnv* env,
                                const jbyteArray& encryption_key_in,
                                unsigned char* encryption_buffer) {
    jbyte* encryption_key = env->GetByteArrayElements(encryption_key_in, nullptr);

    if (encryption_key == nullptr) {
        return false;
    }
    const jsize len = env->GetArrayLength(encryption_key_in);
    FATFS_DEBUG_PRINT("Encryption key has size %d\n", len);

    if (len != sizeof(sgx_aes_gcm_128bit_key_t)) {
        return false;
    }
    memcpy(encryption_buffer, encryption_key, sizeof(sgx_aes_gcm_128bit_key_t));
    env->ReleaseByteArrayElements(encryption_key_in, encryption_key, 0);
    return true;
}

//  The initialization of the persistent disk depends on the present of the
//    file/filesystem path on the host.
//  When loading the enclave, we do an OCall and we check the presence of the file on the host.
static conclave::DiskInitialization getInitializationType(JNIEnv* env, const unsigned char drive, const unsigned long persistent_size) {
    long host_file_size = -1;
    host_disk_get_size_ocall(&host_file_size, drive, persistent_size);
    FATFS_DEBUG_PRINT("Host disk size %ld\n", host_file_size);

    const bool host_has_thrown_exception = (host_file_size == -1);

    if (host_has_thrown_exception) {
        FATFS_DEBUG_PRINT("Disk not initialized, the host has thrown an exception, drive %d\n", drive);
        return conclave::DiskInitialization::ERROR;
    }
    const bool host_file_present = (host_file_size != 0);
    conclave::DiskInitialization initialization;
        
    if (host_file_present) { 
        FATFS_DEBUG_PRINT("Opening disk of size %lu bytes for drive %d\n", host_file_size, drive);
        initialization = conclave::DiskInitialization::OPEN;
    } else {
        FATFS_DEBUG_PRINT("Creating disk for drive %d\n", drive);
        initialization = conclave::DiskInitialization::FORMAT;
    }

    return initialization;
}


static void handleInitException(JNIEnv* env, const FatFsResult result, const std::string& fsType) {
    switch (result) {
    case FatFsResult::MKFS_ABORTED:
        {
            //  The minimum number of sectors in FatFs is 128 for the Fat12 type + 63 header of reserved sectors.
            //  Being the size of the sector equal to 512 bytes, we have (128 + 63) * 512 - 1 = 97791
            const std::string msg("Wrong " + fsType + " filesystem's sizes have been provided, please choose a value bigger than 97791 bytes");
            raiseException(env, msg.c_str());
            return;
        }
    case FatFsResult::WRONG_DRIVE_ID:
    case FatFsResult::MOUNT_FAILED:
        {
            const std::string msg("Unable to initialize the enclave's "+ fsType + " filesystem, potentially corrupted or unencryptable filesystem");
            raiseException(env, msg.c_str(), "java/io/IOException");
            return;
        };
    case FatFsResult::DRIVE_REGISTRATION_FAILED:
    case FatFsResult::MKFS_GENERIC_ERROR:
    case FatFsResult::ROOT_DIRECTORY_MOUNT_FAILED:
    default:
        {
            const std::string msg("Unable to initialize the enclave's "+ fsType + " filesystem");
            raiseException(env, msg.c_str());
            return;
        }
    }
}


//  Main entry point to setup the in-memory and persistent filesystem
//  Note: the "return" instruction after raising an exception is compulsory
//    as when throwing JNI exceptions we immediately want to return to Java/Kotlin
//    so that exceptions are handled properly and we do not want to execute anything else in C++.
//    This is because in JNI throwing an exception does not stop the C++ execution.
JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_setupFileSystems(JNIEnv* env,
                                                                                     jobject,
                                                                                     jlong in_memory_size,
                                                                                     jlong persistent_size,
                                                                                     jstring in_memory_mount_path_in,
                                                                                     jstring persistent_mount_path_in,
                                                                                     jbyteArray encryption_key_in) {
    FATFS_DEBUG_PRINT("Sizes: %lu, %lu\n", in_memory_size, persistent_size);

    if (encryption_key_in == nullptr) {
        raiseException(env, "Filesystems not initialized, key not passed in");
        return;
    }    
    const std::string persistent_mount_path = getJniMountPath(env, persistent_mount_path_in);
    const std::string in_memory_mount_path = getJniMountPath(env, in_memory_mount_path_in);
    FATFS_DEBUG_PRINT("Paths %s %s\n", persistent_mount_path.c_str(), in_memory_mount_path.c_str());

    unsigned char encryption_key[sizeof(sgx_aes_gcm_128bit_key_t)];
    
    bool encryption_key_retrieved = getJniEncryptionKey(env, encryption_key_in, encryption_key);

    if (!encryption_key_retrieved) {
        raiseException(env, "Filesystems not initialized, key not retrieved");
        return;
    }

    if ((unsigned long)in_memory_size > kMaxInMemorySize) {
        const std::string msg("Wrong in-memory filesystem's sizes have been provided, "
                              "please choose a value smaller than " + std::to_string(kMaxInMemorySize + 1) + " bytes");
        raiseException(env, msg.c_str());
        return;
    }

    if ((unsigned long)persistent_size > kMaxPersistentSize) {
        const std::string msg("Wrong persistent filesystem's sizes have been provided, "
                              "please choose a value smaller than " + std::to_string(kMaxPersistentSize + 1) + " bytes");
        raiseException(env, msg.c_str());
        return;
    }
    unsigned char drive = 0;
    /*
      Note: the persistent filesystem, when present, needs to be the first one. This is because
      we are using the drive id to map the filesystem here in the Enclave with the index of 
      the related file representing the filesystem in the Host (FileSystemHandler.kt).
      We can surely remove this assumption and improve this function, but this would require
      some effort that we can postpone.
      TO DO: Improve the mapping between the Enclave persistent filesystem and the Host file
    */
    
    if (persistent_size > 0) {
        conclave::DiskInitialization initialization = getInitializationType(env, drive, persistent_size);
        
        if (initialization == conclave::DiskInitialization::ERROR) {
            //  The Host has thrown an exception as well
            raiseException(env, "Filesystems not initialized");
            return;
        }
        auto filesystem = createFileSystem(FileSystemType::PERSISTENT,
                                           drive++,
                                           persistent_size,
                                           encryption_key,
                                           persistent_mount_path);
        FatFsResult initResult = filesystem->init(initialization);

        if (initResult != FatFsResult::OK) {
            handleInitException(env, initResult, "persistent");
            return;
        }
        filesystems.push_back(filesystem);
    }
    
    if (in_memory_size > 0) {
        auto filesystem = createFileSystem(FileSystemType::IN_MEMORY,
                                           drive++,
                                           in_memory_size,
                                           encryption_key,
                                           in_memory_mount_path);
        FatFsResult initResult = filesystem->init(conclave::DiskInitialization::FORMAT);

        if (initResult != FatFsResult::OK) {
            handleInitException(env, initResult, "in-memory");
            return;
        }
        filesystems.push_back(filesystem);
    }

    jint res = env->GetJavaVM(&jvm);

    if (res != JNI_OK) {
        FATFS_DEBUG_PRINT("JNI Crashed %d\n", -1);
        raiseException(env, "Filesystems not initialized, jni crashed");
        return;
    }    
    return;
}
DLSYM_STATIC {
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_setupFileSystems);
};


static std::string normalizePath(const std::string& path_in) {
    std::string path(path_in);

    if (path.empty() || path[0] != '/') {
        path = currentPath + path;
    }
    return path;
}

/*  The next couple of functions are needed to retrieve the correct instance of FatFsFileManager that
    we are going to use when one of the Posix calls below is executed.
    Based on the file path that the user is handling and given the mount points (getFatFsInstanceFromPath) or
    based on the handle number (getFatFsInstanceFromHandle) which have been previously created and
    returned to the User (or better, the Enclave JVM), we try to determine which filesystem should be used.
    For example, if we have setup the mount points as "/" for the persistent filesystem
    and "/tmp" for the in-memory one, a file like this "/test.txt" will be handled by the persistent
    filesystem, a file like "/tmp/test.txt" will be handled by the in-memory filesystem and
    a file like "/tmptest.txt" will be handled by the persistent one.
*/
static std::shared_ptr<conclave::FatFsFileManager> getFatFsInstanceFromPath(const char* path_in) {

    if (path_in == nullptr || std::string(path_in).empty()) {
        FATFS_DEBUG_PRINT("Error, empty string provided: %d\n", 1);
        return nullptr;
    }

    const std::string path = normalizePath(std::string(path_in));
    std::vector<std::shared_ptr<conclave::FatFsFileManager> > found_instances;
    
    for (auto& it : filesystems) {

        if (it->isPathOwner(path)) {
            found_instances.push_back(it);
        }
    }
    if (found_instances.size() == 1) {
        FATFS_DEBUG_PRINT("Found filesystem for path %s\n", path.c_str());
        return found_instances.at(0);
    } else if (found_instances.size() == 2) {
        //  This is the case where a path has matching two filesystems: one with a mount point "/"
        //    and another with a "child" mount point, for example "/tmp".
        //  The assumption here is that the root is always "/" and that we always have a root.
        const std::string root = "/";

        for (auto& it : found_instances) {
            if (it->getMountPath() != root) {
                FATFS_DEBUG_PRINT("Found filesystem with mount %s for path %s\n", it->getMountPath().c_str(), path.c_str());
                return it;
            }
        }
        FATFS_DEBUG_PRINT("Filesystem not selected, mount checks failed for path %s\n", path.c_str());
        return nullptr;
    } else {
        FATFS_DEBUG_PRINT("Filesystem not found for path: %s\n", path.c_str());
        return nullptr;
    }
};


static std::shared_ptr<conclave::FatFsFileManager> getFatFsInstanceFromHandle(const int fd) {
    FATFS_DEBUG_PRINT("Handle %d\n", fd);

    if (fd == -1) {
        return nullptr;
    }
    for (auto& it : filesystems) {
        if (it->isHandleOwner(fd)) {
            FATFS_DEBUG_PRINT("Filesystem: %s\n", typeid(*it).name());
            return it;
        }
    }

    FATFS_DEBUG_PRINT("Could not find the right filesystem among the found instances for handle %d\n", fd);
    return nullptr;
};


static std::shared_ptr<conclave::FatFsFileManager> getFatFsInstanceFromDir(void* dir) {
    std::vector<std::shared_ptr<conclave::FatFsFileManager> > found_instances;
  
    for (auto& it : filesystems) {

        if (it->isDirOwner(static_cast<DIR*>(dir))) {
            found_instances.push_back(it);
        }
    }
    if (found_instances.size() == 1) {
        FATFS_DEBUG_PRINT("Found filesystem: %d\n", 1);
        return found_instances.at(0);
    } else {
        FATFS_DEBUG_PRINT("Could not find the right filesystem for the input dir: %d\n", 2);
        return nullptr;
    }  
};


//  Replacement of Posix calls
int open_impl(const char* path, int oflag, int& err) {
    FATFS_DEBUG_PRINT("Open %s\n", path);
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->open(path, oflag, err);
};


FILE *fopen_impl(const char *path, const char *mode, int& err) {
    FATFS_DEBUG_PRINT("Fopen %s\n", path);
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return NULL;
    }
    return file_manager->fopen(path, mode, err);
};


ssize_t read_impl(int fd, void* buf, size_t count) {
    FATFS_DEBUG_PRINT("Read %d\n", fd);
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        return -1;
    }
    return file_manager->read(fd, buf, count);
};


ssize_t pread_impl(int fd, void* buf, size_t count, off_t offset) {
    auto file_manager = getFatFsInstanceFromHandle(fd);
    auto res = file_manager->pread(fd, buf, count, offset);
   
    if (res == -1) {
        errno = -1;
    }
    return res;
};


int rename_impl(const char* oldpath, const char* newpath, int& err) {
    DEBUG_PRINT_FUNCTION;

    auto old_file_manager = getFatFsInstanceFromPath(oldpath);
    auto new_file_manager = getFatFsInstanceFromPath(newpath);

    if (old_file_manager == nullptr || new_file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }    
    
    if (old_file_manager != new_file_manager) {
        FATFS_DEBUG_PRINT("Usage of different filesystems types for the paths %s %s is not possible\n", oldpath, newpath);
        err = EXDEV;
        return -1;
    } else {
        return old_file_manager->rename(oldpath, newpath, err);
    }
};


int close_impl(int fd) {
    DEBUG_PRINT_FUNCTION;

    if (fd >= currentFirstAvailableHandle) {
        //  This is happening when closing a dummy descriptor created with "socketpair"
        FATFS_DEBUG_PRINT("Closed dummy handle %u\n", fd);
        std::lock_guard<std::mutex> lock(dummyHandleMutex);
        dummyHandles.erase(fd);
        return 0;
    } else {
        auto file_manager = getFatFsInstanceFromHandle(fd);

        if (file_manager == nullptr) {
            return -1;
        }
        return file_manager->close(fd);
    }
};


off64_t lseek64_impl(int fd, off64_t offset, int whence) { 
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        return -1;
    }
    return file_manager->lseek(fd, offset, whence);
};


ssize_t write_impl(int fd, const void *buf, size_t count) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        return -1;
    }
    return file_manager->write(fd, buf, count);
};


ssize_t pwrite_impl(int fd, const void *buf, size_t count, off_t offset) {
    DEBUG_PRINT_FUNCTION;
    assert(count <= std::numeric_limits<int>::max());
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        return -1;
    }
    return file_manager->pwrite(fd, buf, count, offset);
};


int __fxstat64_impl(int ver, int fd, struct stat64* stat_buf, int& err) {
    FATFS_DEBUG_PRINT("__fxstat64 handle %u\n", fd);
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        return -1;
    }
    return file_manager->fstat(ver, fd, stat_buf, err);
};


int __xstat64_impl(int ver, const char* path, struct stat64* stat_buf, int& err) {
    FATFS_DEBUG_PRINT("__xstat64 handle %s\n", path);
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->stat(ver, path, stat_buf, err);
};


int mkdir_impl(const char* path, mode_t mode, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->mkdir(path, mode);
};


int lstat_impl(const char* path, struct stat* stat_buf, int& err) {
    FATFS_DEBUG_PRINT("lstat handle %s\n", path);

    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->lstat(path, stat_buf, err);
};


int lstat64_impl(const char* path, struct stat64* stat_buf, int& err) {
    FATFS_DEBUG_PRINT("lstat64 handle %s\n", path);
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->lstat64(path, stat_buf, err);
};


int rmdir_impl(const char* path, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->rmdir(path, err);
};


int unlink_impl(const char* path, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->unlink(path, err);
};


int remove_impl(const char* path, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->remove(path, err);
};


int socketpair_impl(int domain, int type, int protocol, int sv[2]) {
    DEBUG_PRINT_FUNCTION;
    std::lock_guard<std::mutex> lock(dummyHandleMutex);
    
    const int handle1 = currentDummyHandle++;
    const int handle2 = currentDummyHandle++;
    
    sv[0] = handle1;
    sv[1] = handle2;
    dummyHandles.insert(handle1);
    dummyHandles.insert(handle2);
    return 0;
};


int dup2_impl(int oldfd, int newfd) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromHandle(oldfd);

    if (file_manager == nullptr) {
        return -1;
    }
    return file_manager->dup2(oldfd, newfd);
};


int access_impl(const char* path, int mode, int& err) {
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->access(path, mode, err);
};


void* opendir_impl(const char* path, int& err) {
    auto file_manager = getFatFsInstanceFromPath(path);

    if (file_manager == nullptr) {
        err = ENOENT;
        return nullptr;
    }
    return file_manager->opendir(path, err);
}


struct dirent64* readdir64_impl(void* dirp, int& err) {
    auto file_manager = getFatFsInstanceFromDir(dirp);

    if (file_manager == nullptr) {
        err = EBADF;
        return nullptr;
    }
    return file_manager->readdir64(dirp, err);
}


struct dirent* readdir_impl(void* dirp, int& err) {
    auto file_manager = getFatFsInstanceFromDir(dirp);

    if (file_manager == nullptr) {
        err = EBADF;
        return nullptr;
    }
    return file_manager->readdir(dirp, err);
}


int closedir_impl(void* dirp, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto file_manager = getFatFsInstanceFromDir(dirp);

    if (file_manager == nullptr) {
        err = EBADF;
        return -1;
    }
    return file_manager->closedir(dirp, err);
}


int ftruncate_impl(int fd, off_t offset, int& err) {
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        err = EBADF;
        return -1;
    }
    return file_manager->ftruncate(fd, offset, err);
}


int fchown_impl(int fd, uid_t owner, gid_t group, int& err) {
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        err = EBADF;
        return -1;
    }
    return file_manager->fchown(fd, owner, group, err);
}


int fchmod_impl(int fd, mode_t mode, int& err) {
    auto file_manager = getFatFsInstanceFromHandle(fd);

    if (file_manager == nullptr) {
        err = EBADF;
        return -1;
    }
    return file_manager->fchmod(fd, mode, err);
}


int utimes_impl(const char *filename, const struct timeval times[2], int& err) {
    auto file_manager = getFatFsInstanceFromPath(filename);
    
    if (file_manager == nullptr) {
        err = ENOENT;
        return -1;
    }
    return file_manager->utimes(filename, times, err);
}

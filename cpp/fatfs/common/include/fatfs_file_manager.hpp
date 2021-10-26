#ifndef _FATFS_FILE_MANAGER
#define _FATFS_FILE_MANAGER

#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <unistd.h>

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <limits>
#include <stdexcept>
#include <fcntl.h>

#include "sgx_tcrypto.h"
#include "enclave_shared_data.h"
#include "substrate_jvm.h"
#include "sys_stat.h"
#include "graal_isolate.h"
#include "vm_enclave_layer.h"
#include "conclave-stat.h"

#include "ff.hpp"
#include "common.hpp"
#include "fatfs_result.hpp"


namespace conclave {
    typedef uint32_t mode_t;
    typedef int FileHandle;

    class FatFsFileManager {

    private:
        std::unordered_map<FileHandle, FIL* > files_;
        std::unordered_map<std::string, FileHandle> file_paths_;
        std::unordered_map<FIL*, FileHandle> inverse_files_map_;
        std::unordered_map<FileHandle, std::string> inverse_file_paths_;    

        std::unordered_map<std::string, DIR* > dir_paths_;
        std::unordered_map<const DIR*, struct dirent* > dirents_;
        std::unordered_map<const DIR*, struct dirent64* > dirents64_;
        std::unordered_map<const DIR*, std::string > inverse_dir_paths_;
        
        std::mutex file_mutex_;

        //  File descriptors 1 and 2 are reserved.
        FileHandle first_handle_;
        FileHandle next_handle_;
        FileHandle last_handle_;

        std::string mount_path_;
        
        std::shared_ptr<FatFsDisk> disk_handler_;

        std::string drive_text_id_;
        
        std::string generateFatFsPath(const char* path);

        int closeInternal(FIL* fil_ptr);

        int unlinkInternal(const std::string& file_path, int& res);

        FRESULT statWithRoot(const char* path_in, FILINFO* info);

        int statInternal(const char* path, struct stat* stat_buf, int& err);

        int statInternal64(const char* path, struct stat64* stat_buf, int& err);
        
        off_t lseekInternal(int fd, off_t offset, int whence);

        FileHandle getNewHandle();
        
        void insertFileHandle(const FileHandle handle,
                              FIL* fil_ptr,
                              const std::string& path); 

        void addDirHandle(DIR* dir_ptr, const std::string& path);       
        
    public:
        virtual ~FatFsFileManager();

        FatFsFileManager() = delete;

        FatFsFileManager(const int first_handle_id,
                         const int max_handle_id,
                         const unsigned char* encryption_key,
                         const std::string& mount_path,
                         const std::shared_ptr<FatFsDisk>& disk_handler);

        std::string getMountPath();

        FatFsResult init(const DiskInitialization init_type);

        //  These couple of functions uses the file/handle maps above
        //    to determine if the path/handle is managed by this file manager
        bool isPathOwner(const std::string& path);

        bool isHandleOwner(const int handle);

        //  This is to determine if the file manager is the owner of the directory
        //    represented by the DIR pointer.
        bool isDirOwner(const DIR* dir);        

        //  Posix calls
        int open(const char* path, int oflag, int& err);
       
        off_t lseek(int fd, off_t offset, int whence);
  
        ssize_t read(int fd, void* buf, size_t count);

        size_t fread(void* ptr, size_t size, size_t nmemb, FILE* stream);

        ssize_t pread(int fd, void* buf, size_t count, off_t offset);
    
        FILE *fdopen(int fd, const char *mode);

        FILE *fopen(const char* path, const char* mode, int& err);

        size_t fwrite(const void* buf, size_t size, size_t count, FILE* fp);

        ssize_t pwrite(int fd, const void* buf, size_t count, off_t offset);
        
        ssize_t write(int fd, const void* buf, size_t count);
        
        int fclose(FILE* fp);    
        
        int close(int fd);

        int fstat(int ver, int fd, struct stat64* stat_buf, int& err);

        int stat(int ver, const char* path, struct stat64* stat_buf, int& err);

        int lstat64(const char* path, struct stat64* stat_buf, int& err);

        int lstat(const char* path, struct stat* stat_buf, int& err);
        
        int mkdir(const char* path, mode_t mode);

        int access(const char* path, mode_t mode, int& err);
        
        int unlink(const char* path, int& res);

        int rmdir(const char* path, int& res);

        int remove(const char* path, int& res);
        
        int chdir(const char* path);

        char *getcwd(char* buf, size_t size);

        int dup2(int oldfd, int newfd);

        void* opendir(const char* dirname, int& err);

        struct dirent* readdir(void* dirp, int& err);

        struct dirent64* readdir64(void* dirp, int& err);
        
        int closedir(void* dirp, int& err);

        int ftruncate(int fd, off_t length, int& err);
    };
};

#endif    //  _FATFS_FILE_MANAGER

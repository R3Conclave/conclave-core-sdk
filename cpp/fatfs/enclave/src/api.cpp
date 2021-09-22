#include <string.h>
#include <assert.h>
#include <limits>
#include <stdexcept>
#include "enclave_shared_data.h"
#include "substrate_jvm.h"
#include "sys_stat.h"
#include "graal_isolate.h"
#include "vm_enclave_layer.h"
#include "conclave-stat.h"
#include "unistd.h"

#include "fatfs_file_manager.hpp"

extern "C" {
    int Java_com_r3_conclave_enclave_internal_fatfs_Filesystem_getSize(graal_isolatethread_t*);
}

static conclave::FatFsFileManager& get_fatfs_instance() {
    static unsigned int size = Java_com_r3_conclave_enclave_internal_fatfs_Filesystem_getSize(r3::conclave::Jvm::instance().jniEnv().get());
    return conclave::FatFsFileManager::instance(ENCLAVE_MEMORY, size);
};

int open_impl(const char *file_path, int oflag) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();    
    return file_manager.open(file_path, oflag);
};


ssize_t read_impl(int fd, void* buf, size_t count) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.read(fd, buf, count);
};


ssize_t pread_impl(int fd, void* buf, size_t count, off_t offset) {
    DEBUG_PRINT_FUNCTION;

    auto& file_manager = get_fatfs_instance();
    auto res = file_manager.pread(fd, buf, count, offset);
   
    if (res == -1) {
        errno = -1;
    }
    return res;
};


int close_impl(int fd) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.close(fd);
};


off64_t lseek64_impl(int fd, off64_t offset, int whence) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.lseek(fd, offset, whence);
};


ssize_t write_impl(int fd, const void *buf, size_t count) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.write(fd, buf, count);
};


ssize_t pwrite_impl(int fd, const void *buf, size_t count, off_t offset) {
    DEBUG_PRINT_FUNCTION;
    assert(count <= std::numeric_limits<int>::max());
    auto& file_manager = get_fatfs_instance();
    return file_manager.pwrite(fd, buf, count, offset);
};


int __fxstat64_impl(int ver, int fd, struct stat64* stat_buf, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    const unsigned int num_bytes = sizeof(struct stat64);    
    return file_manager.fstat(ver, fd, stat_buf, num_bytes, err);
};


int __xstat64_impl(int ver, const char * path, struct stat64* stat_buf, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    const unsigned int num_bytes = sizeof(struct stat64);
    return file_manager.stat(ver, path, stat_buf, num_bytes, err);
};


int mkdir_impl(const char* path, mode_t mode) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.mkdir(path, mode);
};


int lstat_impl(const char* path, struct stat* stat_buf, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.lstat(path, stat_buf, err);
};

int lstat64_impl(const char* path, struct stat64* stat_buf, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.lstat64(path, stat_buf, err);
};


int rmdir_impl(const char* path, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.rmdir(path, err);
};


int unlink_impl(const char* path, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.unlink(path, err);
};


int socketpair_impl(int domain, int type, int protocol, int sv[2]) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.socketpair(domain, type, protocol, sv);
};


int dup2_impl(int oldfd, int newfd) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.dup2(oldfd, newfd);
};

int access_impl(const char* pathname, int mode, int& err) {
    DEBUG_PRINT_FUNCTION;
    auto& file_manager = get_fatfs_instance();
    return file_manager.access(pathname, mode, err);
};


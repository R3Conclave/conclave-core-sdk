//
// OS Stubs for functions declared in sys/stat.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"
#include "sys_stat.h"
#include "conclave-stat.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(umask);

extern "C" {

    int stat64(const char* pathname, struct stat64*) {

        if (pathname[0] == '[') {
            // stat64("[embedded_foo_jar]")
            return -1;
        }
        jni_throw("STUB: stat64(%s)\n", pathname);
        return -1;
    }

    int __fxstat64(int ver, int fildes, struct stat64* stat_buf) {
        enclave_trace("__fxstat64\n");

        // See if this is one of our handled files
        conclave::File* file = conclave::FileManager::instance().fromHandle(fildes);
        if (file) {
            memset(stat_buf, 0, sizeof(struct stat64));
            stat_buf->st_mode = S_IFMT;
            return 0;
        }
        int err = 0;
        const int res = __fxstat64_impl(ver, fildes, stat_buf, err);
        errno = err;
        return res;
    }

    int __xstat64(int ver, const char* path, struct stat64* stat_buf) {
        enclave_trace("__xstat64\n");
        int err = 0;
        const int res = __xstat64_impl(ver, path, stat_buf, err);
        errno = err;
        return res;
    }

    int mkdir(const char* path, mode_t mode) {
        enclave_trace("mkdir\n");
        int err = 0;
        int res = mkdir_impl(path, mode, err);
        errno = err;
        return res;
    }

    int __lxstat(int ver, const char* pathname, struct stat* stat_buf) {
        enclave_trace("__lxstat\n");
        int err = 0;
        const int res = lstat_impl(pathname, stat_buf, err);
        errno = err;
        return res;
    }


    int __lxstat64(int, const char* pathname, struct stat64* stat_buf) {
        enclave_trace("__lxstat64\n");
        int err = 0;
        const int res = lstat64_impl(pathname, stat_buf, err);
        errno = err;
        return res;
    }

    // Stub functions to satisfy the linker    
    int __xstat(int, const char*, struct stat*) {
        enclave_trace("__xstat\n");
	    errno = ENOSYS;
        return -1;
    }

    int __fxstat(int, int, struct stat*) {
        enclave_trace("__fxstat\n");
	    errno = ENOSYS;
        return -1;
    }

    int lstat64(const char* pathname, struct stat64* stat_buf) {
        enclave_trace("lstat64\n");
        int err = 0;
        const int res = lstat64_impl(pathname, stat_buf, err);
        errno = err;
        return res;
    }

    int chmod(const char *pathname, mode_t mode) {
        enclave_trace("chmod\n");
        return 0;
    }

    int fstat(int fd, struct stat *statbuf) {
        enclave_trace("fstat\n");
        errno = ENOSYS;
        return -1;
    }

    int stat(const char *pathname, struct stat *statbuf) {
        enclave_trace("stat\n");
        errno = ENOSYS;
        return -1;
    }

    int lstat(const char *pathname, struct stat *statbuf) {
        enclave_trace("lstat\n");
        errno = ENOSYS;
        return -1;
    }

    int statvfs64 (const char *file, struct statvfs64 *buf) {
        enclave_trace("statvfs64\n");
        errno = ENOSYS;
        return -1;
    }    
}

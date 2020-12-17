//
// OS Stubs for functions declared in sys/stat.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"
#include "sys_stat.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(chmod);
STUB(fstat64);
STUB(fstat);
STUB(stat);
STUB(statvfs64);
STUB(mkdir);
STUB(umask);

extern "C" {

int stat64(const char *pathname, struct stat64*) {
    if (pathname[0] == '[') {
        // stat64("[embedded_foo_jar]")
        return -1;
    }
    jni_throw("STUB: stat64(%s)\n", pathname);
    return -1;
}

int __fxstat64(int ver, int fildes, struct stat64 * stat_buf) {
    enclave_trace("__fxstat64\n");

    // See if this is one of our handled files
    conclave::File* file = conclave::FileManager::instance().fromHandle(fildes);
    if (file) {
        memset(stat_buf, 0, sizeof(struct stat64));
        stat_buf->st_mode = S_IFMT;
        return 0;
    }
    return __fxstat64_impl(ver, fildes, stat_buf);
}

int __xstat64(int ver, const char * path, struct stat64 * stat_buf) {
    enclave_trace("__xstat64\n");
    return -1;
}

int __xstat(int, const char*, struct stat*) {
    enclave_trace("__xstat\n");
    return -1;
}

int __fxstat(int, int , struct stat*) {
    enclave_trace("__fxstat\n");
    return -1;
}

int __lxstat(int, const char*, struct stat*) {
    enclave_trace("__lxstat\n");
    return -1;
}

int __lxstat64(int, const char*, struct stat64*) {
    enclave_trace("__lxstat64\n");
    return -1;
}

}

//
// OS Stubs for functions declared in sys/xattr.h
//
#include "vm_enclave_layer.h"

extern "C" {

    ssize_t fgetxattr(int fd, const char *name, void *value, size_t size) {
        enclave_trace("fgetxattr\n");
        errno = ENOTSUP;
        return -1;
    }

    int fsetxattr(int fd,
                  const char *name,
                  const void *value,
                  size_t size,
                  int flags) {
        enclave_trace("fsetxattr\n");
        errno = ENOTSUP;
        return -1;
    }

    ssize_t flistxattr(int fd, char *list, size_t size) {
        enclave_trace("flistxattr\n");
        errno = ENOTSUP;
        return -1;
    }
}

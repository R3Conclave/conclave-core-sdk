//
// OS Stubs for functions declared in ioctl.h
//
#include "vm_enclave_layer.h"

extern "C" {

    int ioctl(int fd, unsigned long request, ...) {
        enclave_trace("ioctl\n");
        errno = ENOSYS;
        return -1;
    }
}

//
// OS Stubs for functions declared in sys/sendfile.h
//
#include "vm_enclave_layer.h"

extern "C" {

    ssize_t sendfile64(int out_fd, int in_fd, off64_t * offset, size_t count) {
        enclave_trace("sendfile64\n");
        errno = ENOSYS;
        return -1;
    }
}

//
// OS Stubs for functions declared in sys/socket.h
//
#include "vm_enclave_layer.h"

extern "C" {

    typedef unsigned long int nfds_t;

    int poll(struct pollfd *fds, nfds_t nfds, int timeout) {
        errno = ENOSYS;
        return -1;
    }
}

//
// OS Stubs for functions declared in sigthread.h
//
#include "vm_enclave_layer.h"

extern "C" {

int pthread_kill(pthread_t thread, int sig) {
    enclave_trace("pthread_kill\n");
    return 0;
}

}
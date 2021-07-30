//
// OS Stubs for functions declared in semaphore.h
//
#include "vm_enclave_layer.h"

typedef volatile unsigned char atomic_t;
typedef atomic_t sem_t;

extern "C" {

int sem_init(sem_t *sem, int pshared, unsigned int value) {
    enclave_trace("sem_init\n");
    return -1;
}

int sem_destroy(sem_t *sem) {
    enclave_trace("sem_destroy\n");
    return -1;
}

int sem_wait(sem_t *sem) {
    enclave_trace("sem_wait\n");
    return -1;
}

int sem_post(sem_t *sem) {
    enclave_trace("sem_post\n");
    return -1;
}

}
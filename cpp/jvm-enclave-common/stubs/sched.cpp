//
// OS Stubs for functions declared in sched.h
//
#include "vm_enclave_layer.h"

extern "C" {

struct cpu_set_t;

int sched_yield(void) {
    enclave_trace("sched_yield\n");
    return 0;
}

int sched_getaffinity(pid_t pid, size_t cpusetsize, cpu_set_t *mask) {
    enclave_trace("sched_getaffinity\n");
    return -1;
}

}

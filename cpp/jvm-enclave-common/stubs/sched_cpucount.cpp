//
// OS Stubs for functions declared in sched_cpucount.h
//
#include "vm_enclave_layer.h"

extern "C" {

struct cpu_set_t;

int __sched_cpucount(size_t setsize, cpu_set_t *setp) {
   enclave_trace("__sched_cpucount\n");
   return -1;
}

}

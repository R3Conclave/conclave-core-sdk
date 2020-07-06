//
// OS Stubs for functions declared in sched.h
//
#include "vm_enclave_layer.h"

extern "C" {

int sched_yield(void) {
    enclave_trace("sched_yield\n");
    return 0;
}

}

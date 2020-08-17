//
// OS Stubs for functions declared in sys/resource.h
//
#include "vm_enclave_layer.h"

extern "C" {

int getrlimit(int resource, struct rlimit *rlim) {
    enclave_trace("getrlimit(%d)\n", resource);
    if (resource == RLIMIT_NOFILE) {
        rlim->rlim_max = 64;   // Fake FD limit.
    } else {
        rlim->rlim_max = 0;
    }
    return 0;
}

int setrlimit(int resource, const struct rlimit *rlim) {
    enclave_trace("setrlimit(%d)\n", resource);
    return 0;
}

}

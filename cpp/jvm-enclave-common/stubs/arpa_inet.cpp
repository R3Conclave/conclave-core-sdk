//
// OS Stubs for functions declared in arpa/inet.h
//
#include "vm_enclave_layer.h"

extern "C" {
    
int inet_pton(int af, const char *src, void *dst) {
    enclave_trace("inet_pton\n");
    return 0;
}

}

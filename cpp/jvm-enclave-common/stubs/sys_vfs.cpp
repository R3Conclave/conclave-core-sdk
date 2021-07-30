//
// OS Stubs for functions declared in sys/vfs.h
//
#include "vm_enclave_layer.h"

extern "C" {

int statfs(const char *path, struct statfs *buf) {
    enclave_trace("statfs\n");
    return -1;
}

}
//
// OS Stubs for functions declared in dlfcn.h
//
#include "vm_enclave_layer.h"
#include <sgx_utils.h>
#include <sgx_trts.h>
#include <string>
#include <unordered_map>
#include "dlsym_symbols.h"

using namespace std;

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(dlclose);

extern "C" {

void *dlopen(const char *filename, int flags) {
    enclave_trace("dlopen\n");
    return nullptr;
}

void *dlmopen(const char *filename, int flags) {
    enclave_trace("dlmopen\n");
    return nullptr;
}

char *dlerror(){
    enclave_trace("dlerror\n");
    return nullptr;
}

int dlinfo(void *handle, int request, void *info) {
    enclave_trace("dlinfo\n");
    return -1;
}

int dladdr(void* addr, Dl_info* info) {
    enclave_trace("dladdr\n");
    return 0;
}

}

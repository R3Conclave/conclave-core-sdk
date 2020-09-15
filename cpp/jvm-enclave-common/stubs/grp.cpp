//
// OS Stubs for functions declared in grp.h
//
#include "vm_enclave_layer.h"

extern "C" {

int getgrgid_r(const char* name, struct group* grp, char* buf, size_t bufflen, struct group** result) {
    enclave_trace("getgrgid_r\n");
    *result = nullptr;
    return 0;
}

}

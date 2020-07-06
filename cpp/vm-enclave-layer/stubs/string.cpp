//
// OS Stubs for functions declared in string.h
//
#include "vm_enclave_layer.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(strcat);

extern "C" {

char *strcpy(char *dest, const char *src) {
    return strncpy(dest, src, strlen(src) + 1);
}

char *stpcpy(char *dest, const char *src) {
    strcpy(dest, src);
    return dest + strlen(src);
}

char * __xpg_strerror_r(int errnum, char * buf, size_t buflen) {
    enclave_trace("__xpg_strerror_r\n");
    return nullptr;
}

}

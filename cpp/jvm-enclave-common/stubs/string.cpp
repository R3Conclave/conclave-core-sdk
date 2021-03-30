//
// OS Stubs for functions declared in string.h
//
#include "vm_enclave_layer.h"

extern "C" {

// The function below was removed because it doesn't seem to be required.
// Also, it was throwing a warning which was keeping the code from compiling.
char *strcpy(char *dest, const char *src) {
    // The following row was replaced with a `memcpy` because GCC > 8.2 throws the following spurious message when
    // it identifies the "anti-pattern" of using the source's length to establish the length of the string.
    // `specified bound depends on the length of the source argument [-Werror=stringop-overflow=]`
    // return strncpy(dest, src, strlen(src) + 1);

    return (char*) memcpy(dest, src, strlen(src) + 1);
}

char *stpcpy(char *dest, const char *src) {
    strcpy(dest, src);
    return dest + strlen(src);
}

char * __xpg_strerror_r(int errnum, char * buf, size_t buflen) {
    enclave_trace("__xpg_strerror_r\n");
    return nullptr;
}

char *strcat(char *destination, const char *source) {
    const size_t len = strlen(destination);
    strcpy(&destination[len], source);
    return destination;
}

}

//
// OS Stubs for functions declared in locale.h
//
#include "vm_enclave_layer.h"

extern "C" {

char *setlocale(int, const char *locale) {
    if (locale && *locale != '\0')
        enclave_trace("setlocale(%s)\n", locale);
    return (char*) "C";
}

}

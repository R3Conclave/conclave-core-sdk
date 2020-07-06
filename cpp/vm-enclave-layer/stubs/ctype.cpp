//
// OS Stubs for functions declared in ctype.h
//
#include "vm_enclave_layer.h"

extern "C" {

const unsigned short * * __ctype_b_loc() {
    jni_throw("STUB: __ctype_b_loc");
    return NULL;
}

}

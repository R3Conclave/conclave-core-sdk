//
// OS Stubs for functions declared in dirent.h
//
#include "vm_enclave_layer.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(closedir);
STUB(opendir);
STUB(readdir64_r);
STUB(readdir64);

extern "C" {

}

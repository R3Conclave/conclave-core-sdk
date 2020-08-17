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
STUB(dlopen);


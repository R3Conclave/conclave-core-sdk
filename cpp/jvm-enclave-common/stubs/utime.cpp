//
// OS Stubs for functions declared in utime.h
//
#include "vm_enclave_layer.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker

extern "C" {
    
    int utimes(const char *filename, const struct timeval times[2]) {
        enclave_trace("utimes(%s)\n", filename);
        int err = 0;
        const int res = utimes_impl(filename, times, err);
        errno = err;
        return res;
    }
}

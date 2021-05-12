//
// OS Stubs for functions declared in sys/time.h
//
#include "vm_enclave_layer.h"
#include "enclave_shared_data.h"

#if __GNUC__ >= 7
// The new GCC flags a warnings which is then escalated to an error if we check a parameter for
// being null when the header says it shouldn't be null. Unfortunately gettimeofday specifies
// it's OK to pass null as the first param, it just doesn't make sense so gets warned. Suppress
// this here, we don't have complex enough code in this file to care.
#pragma GCC diagnostic ignored "-Wnonnull-compare"
#endif

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(timezone);

extern "C" {

int gettimeofday(struct timeval *tv, struct timezone *tz) {
    if (tv) {
        r3::conclave::EnclaveSharedData::instance().real_time(*tv);
    }
    if (tz) {
        tz->tz_dsttime = 0;
        tz->tz_minuteswest = 0;
    }
    return 0;
}

}

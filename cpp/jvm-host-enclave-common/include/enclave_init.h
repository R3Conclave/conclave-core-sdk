#pragma once

#include <stdint.h>

namespace r3 { namespace conclave {
/**
 * A structure that allows data to be exchanged to and from the enclave at the point the enclave
 * is started.
 */
struct EnclaveInit {

    // Enclave -> Host:
    // The timeout value when detecting thread deadlocks. If all enclave threads are busy whilst 
    // there are new threads waiting to be created and no thread has exited before this timeout
    // is reached then an exception is thrown from the thread creation. This is configured by
    // the developer as part of the Gradle conclave configuration. A value of 0 means there is no
    // deadlock detect.
    uint64_t deadlock_timeout_seconds = 0;
};

}}

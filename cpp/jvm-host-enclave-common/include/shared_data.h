#pragma once

#include <stdint.h>
#include <atomic>

namespace r3 { namespace conclave {
/**
 * This file defines a layout for a block of memory that is shared outside of EPC between the host and the enclave.
 * It can be used to transfer information from the host to the enclave without having to invoke an ocall/ecall.
 * 
 * IMPORTANT NOTE: The enclave cannot trust any value written to this structure. It must assume that the host
 * is malicious and can manipulate the data in order to compromise the enclave
 */
struct SharedData {
    SharedData() = default;
    SharedData(const SharedData& other) {
        *this = other;
    }
    
    SharedData& operator=(volatile const SharedData& other) {
         real_time = other.real_time;
         return *this;
     }

    // System time in nanoseconds as read from the host using clock_gettime(CLOCK_REALTIME).
    volatile uint64_t real_time = 0;
};

}}

#pragma once

#include <stdint.h>

namespace r3 { namespace conclave {
/**
 * This file defines a layout for a block of memory that is shared outside of EPC between the host and the enclave.
 * It can be used to transfer information from the host to the enclave without having to invoke an ocall/ecall.
 * 
 * IMPORTANT NOTE: The enclave cannot trust any value written to this structure. It must assume that the host
 * is malicious and can manipulate the data in order to compromise the enclave
 */
struct SharedData {

    SharedData() { }
    SharedData(const SharedData& other) {
        // We explicitly define a copy constructor to perform a member-wise copy to prevent the
        // compiler generating a byte-wise copy (although it probably won't). This is to minimise the
        // risk of a read in the enclave at the same time as a write in the host causing a problem.
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

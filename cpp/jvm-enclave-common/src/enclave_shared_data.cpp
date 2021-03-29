#include "enclave_shared_data.h"

#if !defined(UNIT_TEST)
#include "vm_enclave_layer.h"
#include <jvm_t.h>
#endif

#include "conclave-timespec.h"

namespace r3 { namespace conclave {

EnclaveSharedData& EnclaveSharedData::instance() {
#if !defined(UNIT_TEST)
    static EnclaveSharedData so;
    return so;
#else 
    // This is leaked but that's ok for our unit tests.
    return *new EnclaveSharedData();
#endif
}

uint64_t EnclaveSharedData::real_time() {
    init();
    SharedData sd;
    getSharedData(sd);

    // Ensure time cannot go backwards
    if (sd.real_time > last_time_) {
        last_time_ = sd.real_time;
    }
    return last_time_;
}

void EnclaveSharedData::real_time(struct timespec* t) {
    if (t) {
        uint64_t ns = real_time();
        t->tv_sec = ns / NS_PER_SEC;
        t->tv_nsec = ns % NS_PER_SEC;
    }
}

void EnclaveSharedData::real_time(struct timeval* t) {
    if (t) {
        uint64_t ns = real_time();
        t->tv_sec = ns / NS_PER_SEC;
        t->tv_usec = (ns % NS_PER_SEC) / 1000;
    }
}

EnclaveSharedData::EnclaveSharedData() : shared_data_(nullptr), last_time_(0) {
}

EnclaveSharedData::~EnclaveSharedData() {
}

void EnclaveSharedData::getSharedData(SharedData& sd) {
    // On each access, grab the pointer and check it lies outside the enclave
    volatile SharedData* p = shared_data_;
    if (!p || !sgx_is_outside_enclave((void*)p, sizeof(SharedData))) {
        // This suggests a malicious host so just abort the enclave.
        abort();
    }
    sd = *p;
}

void EnclaveSharedData::init() {
    if (!shared_data_) {
        void* p = nullptr;
        if (shared_data_ocall(&p) == SGX_SUCCESS) {
            // Make sure the pointer points outside the enclave for the entire size
            // we are going to be reading.
            if (!sgx_is_outside_enclave(p, sizeof(SharedData))) {
                // This suggests a malicious host so just abort the enclave.
                abort();
            }
            shared_data_ = static_cast<volatile SharedData*>(p);
        }
        else {
            jni_throw("Could not get enclave shared data via ocall to host");
        }
    }
}

}}

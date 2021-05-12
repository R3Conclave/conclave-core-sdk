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
    // Here we are trying to avoid "over-locking" the mutex in cases where it's not necessary.
    // To do so, we are implementing the Double-Checked locking pattern.
    // For more information, refer to this (great) article: https://preshing.com/20130930/double-checked-locking-is-fixed-in-cpp11/

    // The prefix "local_" was used to provide a more explicit differentiation against its atomics counterparts.
    uint64_t local_real_time = sd.real_time;
    uint64_t local_last_time = last_time_.load(std::memory_order_acquire);
    if (local_real_time > local_last_time) {
        sgx_scoped_lock lock(spinlock_);
        local_real_time = sd.real_time;
        local_last_time = last_time_.load(std::memory_order_relaxed);
        if (local_real_time > local_last_time) {
            local_last_time = local_real_time;
            last_time_.store(local_real_time, std::memory_order_release);
        }
    }
    return local_last_time ;
}

void EnclaveSharedData::real_time(timespec& t) {
    uint64_t ns = real_time();
    t.tv_sec = ns / NS_PER_SEC;
    t.tv_nsec = ns % NS_PER_SEC;
}

void EnclaveSharedData::real_time(timeval& t) {
    uint64_t ns = real_time();
    t.tv_sec = ns / NS_PER_SEC;
    t.tv_usec = (ns % NS_PER_SEC) / 1000;
}

EnclaveSharedData::EnclaveSharedData() : shared_data_(nullptr), last_time_(0) {
}

EnclaveSharedData::~EnclaveSharedData() {
}

void EnclaveSharedData::getSharedData(SharedData& sd) const {
    // On each access, grab the pointer and check it lies outside the enclave
    volatile SharedData* p = shared_data_.load(std::memory_order_acquire);
    if (!p || !sgx_is_outside_enclave((void*)p, sizeof(SharedData))) {
        // This suggests a malicious host so just abort the enclave.
        abort();
    }
    sd = *p;
}

void EnclaveSharedData::init() {
    // Double-Checked locking pattern.
    // The prefix "scope" was used to provide a more explicit differentiation against its atomics counterpart.
    volatile SharedData* local_shared_data = shared_data_.load(std::memory_order_acquire);
    if (!local_shared_data) {
        sgx_scoped_lock lock(spinlock_);
        local_shared_data = shared_data_.load(std::memory_order_relaxed);
        if (!local_shared_data) {
            void* p = nullptr;
            if (shared_data_ocall(&p) == SGX_SUCCESS) {
                // Make sure the pointer points outside the enclave for the entire size
                // we are going to be reading.
                if (!sgx_is_outside_enclave(p, sizeof(SharedData))) {
                    // This suggests a malicious host so just abort the enclave.
                    abort();
                }
                shared_data_.store(static_cast<volatile SharedData*>(p), std::memory_order_release);
            }
            else {
                jni_throw("Could not get enclave shared data via ocall to host");
            }
        }
    }
}

}}

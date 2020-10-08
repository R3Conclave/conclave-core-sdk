#include "enclave_shared_data.h"
#include "vm_enclave_layer.h"
#include <jvm_t.h>

namespace r3 { namespace conclave {

EnclaveSharedData& EnclaveSharedData::instance() {
    static EnclaveSharedData so;
    return so;
}

uint64_t EnclaveSharedData::real_time() {
    init();
    if (!shared_data_) {
        return 0;
    }
    return shared_data_->real_time;
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

EnclaveSharedData::EnclaveSharedData() : shared_data_(nullptr) {
}

EnclaveSharedData::~EnclaveSharedData() {
}

void EnclaveSharedData::init() {
    if (!shared_data_) {
        void* p = nullptr;
        if (shared_data_ocall(&p) == SGX_SUCCESS) {
            shared_data_ = static_cast<SharedData*>(p);
        }
        else {
            jni_throw("Could not get enclave shared data via ocall to host");
        }
    }
}

}}

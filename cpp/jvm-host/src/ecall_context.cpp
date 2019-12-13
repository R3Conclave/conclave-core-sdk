#include <sgx_eid.h>
#include <ecall_context.h>
#include <vector>
#include <sgx.h>

namespace {
    struct Context {
        sgx_enclave_id_t enclaveId;
        JNIEnv *jniEnv;
        std::shared_ptr<std::promise<sgx_status_t>> threadStartPromise;
    };
    thread_local std::vector<Context> contextStack;
}

EcallContext::EcallContext(sgx_enclave_id_t id,
                           JNIEnv *jniEnv,
                           std::shared_ptr<std::promise<sgx_status_t>> p) {
    contextStack.push_back(Context { id, jniEnv, p });
}

EcallContext::~EcallContext() {
    contextStack.pop_back();
}

sgx_enclave_id_t EcallContext::getEnclaveId(){
    return contextStack.back().enclaveId;
}

bool EcallContext::available() {
   return !contextStack.empty();
}

JNIEnv *EcallContext::getJniEnv() {
    return contextStack.back().jniEnv;
}

void EcallContext::setThreadStartStatus(sgx_status_t status) {
    if (contextStack.back().threadStartPromise) {
        (contextStack.back().threadStartPromise)->set_value(status);
        (contextStack.back().threadStartPromise).reset();
    }
}


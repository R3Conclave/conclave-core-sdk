#include <cassert>
#include <pthread.h>
#include <jvm_u.h>
#include <ecall_context.h>
#include <cstdio>
#include <sgx_errors.h>
#include <stdexcept>
#include <jni_utils.h>
#include <map>
#include <future>

extern "C" {

sgx_status_t ocall_request_thread() {
    const auto enclave_id = EcallContext::getEnclaveId();
    JavaVM *java_vm = nullptr;
    if (!EcallContext::available()) {
        throw std::runtime_error("Ocall missing an ecall context structure");
    }
    EcallContext::getJniEnv()->GetJavaVM(&java_vm);

    auto thread_started_promise = std::make_shared<std::promise<sgx_status_t>>();
    auto thread_started_future = thread_started_promise->get_future();

    std::thread{
        [enclave_id, java_vm, thread_started_promise] () {
            JNIEnv *jniEnv = nullptr;
            java_vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&jniEnv), nullptr);
            EcallContext context(enclave_id, jniEnv, thread_started_promise);
            const auto ret = ecall_attach_thread(enclave_id);
            if (ret != SGX_SUCCESS) {
                fprintf(stderr, "JVM enclave thread returned an error %s\n", getErrorMessage(ret));
                EcallContext::setThreadStartStatus(ret);
            }
        }
    }.detach();

    const auto ret = thread_started_future.get();
    return ret;
}

void ocall_complete_request_thread() {
    EcallContext::setThreadStartStatus(SGX_SUCCESS);
}

}
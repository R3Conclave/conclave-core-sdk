#pragma once

#include <sgx.h>
#include <sgx_eid.h>
#include <jni.h>
#include <future>
#include <memory>

class EcallContext {
public:
    explicit EcallContext(sgx_enclave_id_t id, JNIEnv *jniEnv, std::shared_ptr<std::promise<sgx_status_t>>);
    ~EcallContext();
    static bool available();
    static sgx_enclave_id_t getEnclaveId();
    static JNIEnv *getJniEnv();
    static void setThreadStartStatus(sgx_status_t);
};

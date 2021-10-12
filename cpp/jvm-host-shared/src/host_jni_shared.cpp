#include <enclave_platform.h>
#include <host_jni_shared.h>
#include <jni_utils.h>

#include "cpu_info.h"

static void raisePlatformSupportException(JNIEnv *jniEnv, const char *message) {
    raiseException(jniEnv, message, "com/r3/conclave/host/PlatformSupportException");
}

void JNICALL Java_com_r3_conclave_host_internal_NativeShared_checkPlatformEnclaveSupport
        (JNIEnv *jniEnv, jobject, jboolean requireHardwareSupport) {
    std::string message;
    if (!checkEnclaveSupport(requireHardwareSupport, message)) {
        // SGX is not enabled
        raisePlatformSupportException(jniEnv, message.c_str());
    }
}

void JNICALL Java_com_r3_conclave_host_internal_NativeShared_enablePlatformHardwareEnclaveSupport
        (JNIEnv *jniEnv, jobject) {
    std::string message;
    if (!enableHardwareEnclaveSupport(message)) {
        // Failed to enable SGX in software
        raisePlatformSupportException(jniEnv, message.c_str());
    }
}

JNIEXPORT jlong JNICALL Java_com_r3_conclave_host_internal_NativeShared_getCpuFeatures(JNIEnv *, jobject) {
    uint64_t cpu_features{};
    r3::conclave::get_cpu_features_ext(&cpu_features);
    return static_cast<jlong>(cpu_features);
}
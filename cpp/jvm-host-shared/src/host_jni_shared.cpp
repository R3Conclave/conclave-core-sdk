#include <enclave_platform.h>
#include <host_jni_shared.h>
#include <jni_utils.h>

#include "cpu_info.h"

static void raiseEnclaveLoadException(JNIEnv *jniEnv, const char *message) {
    raiseException(jniEnv, message, "com/r3/conclave/host/EnclaveLoadException");
}

void JNICALL Java_com_r3_conclave_host_internal_NativeShared_checkPlatformSupportsEnclaves
        (JNIEnv *jniEnv, jobject, jboolean enableSupport) {
    std::string message;
    bool was_enabled = false;
    if (!checkAndEnableEnclaveSupport(enableSupport, was_enabled, message)) {
        // SGX not enabled
        raiseEnclaveLoadException(jniEnv, message.c_str());
    }
}

JNIEXPORT jlong JNICALL Java_com_r3_conclave_host_internal_NativeShared_getCpuFeatures(JNIEnv *, jobject) {
    uint64_t cpu_features{};
    r3::conclave::get_cpu_features_ext(&cpu_features);
    return static_cast<jlong>(cpu_features);
}
#include <host_jni_shared.h>
#include <enclave_platform.h>
#include <jni_utils.h>

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


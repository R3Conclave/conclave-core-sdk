#include <jni_utils.h>
#include <singleton_jvm.h>
#include <enclave_thread.h>

namespace {

// TODO: Fix C++ exception catching in SGX and terminate with an exception instead of aborting
void abortOnJniException(JNIEnv *jniEnv) {
    if (jniEnv->ExceptionCheck() == JNI_TRUE) {
        jniEnv->ExceptionDescribe();
        abort();
    }
}

extern "C" {

void jvm_ecall(void *bufferIn, int bufferInLen) {
    using namespace r3::conclave;
    auto &jvm = Jvm::instance();
    auto jniEnv = jvm.attach_current_thread();
    if (!jniEnv) {
        if (!jvm.is_alive()) {
            // TODO: consider raise an exception in the host
            throw std::runtime_error("Attempt attaching new thread after enclave destruction started");
        }
    }
    JniScopedRef<jbyteArray> jarrayIn {jniEnv->NewByteArray(bufferInLen), jniEnv.get()};
    jniEnv->SetByteArrayRegion(jarrayIn.value(), 0, bufferInLen, static_cast<const jbyte *>(bufferIn));
    auto NativeEnvClass = jniEnv->FindClass("com/r3/conclave/enclave/internal/NativeEnclaveEnvironment");
    abortOnJniException(jniEnv.get());
    auto methodId = jniEnv->GetStaticMethodID(NativeEnvClass, "enclaveEntry", "([B)V");
    abortOnJniException(jniEnv.get());
    jniEnv->CallStaticObjectMethod(NativeEnvClass, methodId, jarrayIn.value());
    abortOnJniException(jniEnv.get());
}

void ecall_finalize_enclave() {
    using namespace r3::conclave;
    // Stop all enclave threads and prevent new one from entering
    Jvm::instance().close();
    EnclaveThreadFactory::shutdown();
}

}
}

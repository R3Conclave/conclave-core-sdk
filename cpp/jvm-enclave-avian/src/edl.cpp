#include <jni_utils.h>
#include <singleton_jvm.h>
#include <enclave_thread.h>
#include "enclave_shared_data.h"
#include "enclave_init.h"

// These two symbols are defined as parameters to the linker when running native-image.
// __ImageBase is a symbol that is at the address at the base of the image. __DeadlockTimeout is
// a symbol at the fake address of &__ImageBase + the deadlock timeout value configured as
// part of the Gradle enclave configuration.
// We can subtract one address from the other to get the actual value.
extern unsigned long __ImageBase;
extern unsigned long __DeadlockTimeout;
static uint64_t deadlock_timeout = (uint64_t)((uint64_t)&__DeadlockTimeout - (uint64_t)&__ImageBase);

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

    // Make sure this enclave has determined the host shared data address
    EnclaveSharedData::instance().init();

    JniScopedRef<jbyteArray> jarrayIn {jniEnv->NewByteArray(bufferInLen), jniEnv.get()};
    jniEnv->SetByteArrayRegion(jarrayIn.value(), 0, bufferInLen, static_cast<const jbyte *>(bufferIn));
    auto NativeEnvClass = jniEnv->FindClass("com/r3/conclave/enclave/internal/NativeEnclaveEnvironment");
    abortOnJniException(jniEnv.get());
    auto methodId = jniEnv->GetStaticMethodID(NativeEnvClass, "enclaveEntry", "([B)V");
    abortOnJniException(jniEnv.get());
    jniEnv->CallStaticObjectMethod(NativeEnvClass, methodId, jarrayIn.value());
    abortOnJniException(jniEnv.get());
}

void ecall_initialise_enclave(void* initStruct, int initStructLen) {
    if (!initStruct ||(initStructLen != sizeof(r3::conclave::EnclaveInit))) {
        throw std::runtime_error("Invalid configuration structure passed to ecall_initialise_enclave()");
    }
    r3::conclave::EnclaveInit* ei = static_cast<r3::conclave::EnclaveInit*>(initStruct);
    ei->deadlock_timeout_seconds = deadlock_timeout;
}

void ecall_finalize_enclave() {
    using namespace r3::conclave;
    // Stop all enclave threads and prevent new one from entering
    Jvm::instance().close();
    EnclaveThreadFactory::shutdown();
}

}
}

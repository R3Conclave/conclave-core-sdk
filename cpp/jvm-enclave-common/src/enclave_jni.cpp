#include <jni.h>
#include <enclave_jni.h>
#include <os_support.h>
#include <cstdlib>
#include <sgx_eid.h>
#include <algorithm>
#include <jni_utils.h>
#include <jvm_t.h>
#include <stdexcept>
#include <sgx_errors.h>
#include <sgx_trts.h>
#include <string>
#include <singleton_jvm.h>
#include <dlsym_symbols.h>
#include <sgx_utils.h>
#include <enclave_thread.h>
#include <aex_assert.h>

namespace {

// TODO: Fix C++ exception catching in SGX and terminate with an exception instead of aborting
void abortOnJniException(JNIEnv *jniEnv) {
    if (jniEnv->ExceptionCheck() == JNI_TRUE) {
        jniEnv->ExceptionDescribe();
        abort();
    }
}

}

extern "C" {

extern const uint8_t _binary_app_jar_start[];
extern const uint8_t _binary_app_jar_end[];

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
    auto NativeApiClass = jniEnv->FindClass("com/r3/conclave/enclave/internal/NativeEnclaveApi");
    abortOnJniException(jniEnv.get());
    auto methodId = jniEnv->GetStaticMethodID(NativeApiClass, "enclaveEntry", "([B)V");
    abortOnJniException(jniEnv.get());
    jniEnv->CallStaticObjectMethod(NativeApiClass, methodId, jarrayIn.value());
    abortOnJniException(jniEnv.get());
}

void ecall_finalize_enclave() {
    using namespace r3::conclave;
    // Stop all enclave threads and prevent new one from entering
    Jvm::instance().close();
    EnclaveThreadFactory::shutdown();
}

JNIEXPORT void JNICALL Java_com_r3_conclave_core_enclave_internal_Native_jvmOcall
        (JNIEnv *jniEnv, jobject, jbyteArray data) {
    auto size = jniEnv->GetArrayLength(data);
    abortOnJniException(jniEnv);
    auto inputBuffer = jniEnv->GetByteArrayElements(data, nullptr);
    abortOnJniException(jniEnv);
    auto returnCode = jvm_ocall(inputBuffer, size);
    jniEnv->ReleaseByteArrayElements(data, inputBuffer, 0);
    if (returnCode != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(returnCode));
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_core_enclave_internal_Native_readAppJarChunk
        (JNIEnv *jniEnv, jobject, jlong jarOffset, jbyteArray dest, jint destOffset, jint length) {
    static unsigned long jarSize = _binary_app_jar_end - _binary_app_jar_start;
    auto remainingBytes = jarSize - jarOffset;
    if (remainingBytes <= 0) {
        return 0;
    }
    auto destSize = jniEnv->GetArrayLength(dest);
    auto numberOfBytesToCopy = std::min(std::min((destSize), static_cast<jsize>(length)), static_cast<jsize>(remainingBytes));

    jniEnv->SetByteArrayRegion(dest, destOffset, numberOfBytesToCopy,
                               reinterpret_cast<const jbyte *>(&_binary_app_jar_start[jarOffset]));

    return numberOfBytesToCopy;
}

JNIEXPORT void JNICALL Java_com_r3_conclave_core_enclave_internal_Native_createReport
        (JNIEnv *jniEnv, jobject, jbyteArray targetInfoIn, jbyteArray reportDataIn, jbyteArray reportOut) {
    jbyte *target_info = nullptr;
    if (targetInfoIn != nullptr) {
        target_info = jniEnv->GetByteArrayElements(targetInfoIn, nullptr);
    }
    jbyte *report_data = nullptr;
    if (reportDataIn != nullptr) {
        report_data = jniEnv->GetByteArrayElements(reportDataIn, nullptr);
    }
    auto *report = jniEnv->GetByteArrayElements(reportOut, nullptr);
    auto returnCode = sgx_create_report(
            reinterpret_cast<const sgx_target_info_t *>(target_info),
            reinterpret_cast<const sgx_report_data_t *>(report_data),
            reinterpret_cast<sgx_report_t *>(report)
    );
    if (target_info != nullptr) {
        jniEnv->ReleaseByteArrayElements(targetInfoIn, target_info, JNI_ABORT);
    }
    if (report_data != nullptr) {
        jniEnv->ReleaseByteArrayElements(reportDataIn, report_data, JNI_ABORT);
    }
    if (returnCode != SGX_SUCCESS) {
        jniEnv->ReleaseByteArrayElements(reportOut, report, JNI_ABORT);
        raiseException(jniEnv, getErrorMessage(returnCode));
    } else {
        jniEnv->ReleaseByteArrayElements(reportOut, report, 0);
    }
}

JNIEXPORT void JNICALL Java_com_r3_conclave_core_enclave_internal_Native_getRandomBytes
        (JNIEnv *jniEnv, jobject, jbyteArray output, jint offset, jint length) {
    if (length < 0) {
        raiseException(jniEnv, "Please specify a non-negative length");
        return;
    }

    if (offset < 0) {
        raiseException(jniEnv, "Please specify a non-negative offset");
        return;
    }

    JniPtr<uint8_t> rng_output(jniEnv, output);
    auto ret = sgx_read_rand(rng_output.ptr + offset, length);
    if (ret == SGX_SUCCESS) {
        rng_output.releaseMode = 0; // to write back to the jvm
    }  else {
        raiseException(jniEnv, getErrorMessage(ret));
    }
}

JNIEXPORT jboolean JNICALL Java_com_r3_conclave_core_enclave_internal_Native_isEnclaveSimulation
        (JNIEnv *, jobject) {
#ifdef SGX_SIM
    return static_cast<jboolean>(true);
#else
    return static_cast<jboolean>(false);
#endif
}

DLSYM_STATIC {
    DLSYM_ADD(Java_com_r3_conclave_core_enclave_internal_Native_jvmOcall);
    DLSYM_ADD(Java_com_r3_conclave_core_enclave_internal_Native_readAppJarChunk);
    DLSYM_ADD(Java_com_r3_conclave_core_enclave_internal_Native_createReport);
    DLSYM_ADD(Java_com_r3_conclave_core_enclave_internal_Native_getRandomBytes);
    DLSYM_ADD(Java_com_r3_conclave_core_enclave_internal_Native_isEnclaveSimulation);
};

}

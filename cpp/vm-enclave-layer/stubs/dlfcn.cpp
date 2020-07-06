//
// OS Stubs for functions declared in dlfcn.h
//
#include "vm_enclave_layer.h"
#include <sgx_utils.h>
#include <sgx_trts.h>
#include <string>
#include <unordered_map>
#include "dlsym_symbols.h"

using namespace std;

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(dlopen);

#if 0
template <class T>
static inline void* voidPointer(T function)
{
    void* p;
    memcpy(&p, &function, sizeof(void*));
    return p;
}

static void abortOnJniException(JNIEnv *jniEnv) {
    if (jniEnv->ExceptionCheck() == JNI_TRUE) {
        jniEnv->ExceptionDescribe();
        abort();
    }
}

jint JNICALL JNI_OnLoad_java(JavaVM*, void*) {
  return JNI_VERSION_1_8;
}
jint JNICALL JNI_OnLoad_zip(JavaVM*, void*) {
  return JNI_VERSION_1_8;
}

JNIEXPORT jboolean JNICALL Java_com_r3_conclave_enclave_internal_Native_isEnclaveSimulation
        (JNIEnv *, jobject) {
    return static_cast<jboolean>(true);
}

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_jvmOcall
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

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_createReport
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

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_getRandomBytes
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

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_sgxKey
        (JNIEnv* jniEnv, jobject,
         jint keyType, jint keyPolicy, jboolean cpuSvn, jbyteArray keyOut, jint keyOutOffset, jint keyOutSize) {
    const auto ui16KeyType      = static_cast<uint16_t>(keyType);
    const auto ui16KeyPolicy    = static_cast<uint16_t>(keyPolicy);
    const auto ui32KeyOutSize = static_cast<uint32_t>(keyOutSize);
    const auto ui32KeyOutOffset = static_cast<uint32_t>(keyOutOffset);

    if (ui16KeyType > SGX_KEYSELECT_SEAL) {
        raiseException(jniEnv, "invalid keyType");
        return;
    }

    if (ui16KeyPolicy & ~(SGX_KEYPOLICY_MRENCLAVE | SGX_KEYPOLICY_MRSIGNER | SGX_KEYPOLICY_NOISVPRODID |
                         SGX_KEYPOLICY_CONFIGID | SGX_KEYPOLICY_ISVFAMILYID | SGX_KEYPOLICY_ISVEXTPRODID)) {
        raiseException(jniEnv, "use of reserved keyPolicy bits");
        return;
    }

    sgx_key_128bit_t key{};

    if ((ui32KeyOutSize + ui32KeyOutOffset) > sizeof(key) ||
            (static_cast<uint32_t>(jniEnv->GetArrayLength(keyOut)) + ui32KeyOutOffset) > sizeof(key)) {
        raiseException(jniEnv, "keyOut not big enough");
        return;
    }

    sgx_key_request_t req{};

    if (cpuSvn) {
        sgx_report_t report;
        auto ret = sgx_create_report(
                nullptr,
                nullptr,
                &report
        );

        if (ret != SGX_SUCCESS) {
            raiseException(jniEnv, getErrorMessage(ret));
            return;
        }

        memcpy(&req.cpu_svn, &report.body.cpu_svn, sizeof(report.body.cpu_svn));
    }

    req.key_name    = ui16KeyType;
    req.key_policy  = ui16KeyPolicy;

    auto res = sgx_get_key(&req, &key);

    if (res != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(res));
        return;
    }

    JniPtr<uint8_t> jpKeyOut(jniEnv, keyOut);
    memcpy(jpKeyOut.ptr + keyOutOffset, key, sizeof(key));

    jpKeyOut.releaseMode = 0; // to write back to the jvm
}

extern "C" {

void *dlsym(void *handle, const char *symbol) {
    enclave_trace("dlsym(symbol: %s)\n", symbol);
    static const unordered_map<string, void*> symbols({
        { "JNI_OnLoad_java", voidPointer(JNI_OnLoad_java) },
        { "JNI_OnLoad_zip", voidPointer(JNI_OnLoad_zip) },
        { "Java_com_r3_conclave_enclave_internal_Native_isEnclaveSimulation", voidPointer(Java_com_r3_conclave_enclave_internal_Native_isEnclaveSimulation) },
        { "Java_com_r3_conclave_enclave_internal_Native_jvmOcall", voidPointer(Java_com_r3_conclave_enclave_internal_Native_jvmOcall) },
        { "Java_com_r3_conclave_enclave_internal_Native_createReport", voidPointer(Java_com_r3_conclave_enclave_internal_Native_createReport) },
        { "Java_com_r3_conclave_enclave_internal_Native_getRandomBytes", voidPointer(Java_com_r3_conclave_enclave_internal_Native_getRandomBytes) },
        { "Java_com_r3_conclave_enclave_internal_Native_sgxKey", voidPointer(Java_com_r3_conclave_enclave_internal_Native_sgxKey) }
    });

    auto itr = symbols.find(symbol);
    if (itr != symbols.end()) {
        return itr->second;
    }
    enclave_trace("dlsym: symbol not found\n");
    return nullptr;
}

}
#endif

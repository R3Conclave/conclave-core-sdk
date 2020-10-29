#include <host_jni.h>
#include <sgx_urts.h>
#include <enclave_metadata.h>
#include <jvm_u.h>
#include <iostream>
#include <string>
#include <ecall_context.h>
#include <sgx_errors.h>
#include <sgx_device_status.h>
#include <jni_utils.h>
#include <sgx_uae_epid.h>
#include <sys/mman.h>
#include <parser/elfparser.h>
#include <internal/enclave_creator.h>
#include <fclose_guard.h>
#include <munmap_guard.h>
#include <enclave_platform.h>
#include <dcap.h>
#include <mutex>
#include "enclave_console.h"
#include "host_shared_data.h"

// TODO pool buffers in ecalls/ocalls

static void raiseEnclaveLoadException(JNIEnv *jniEnv, const char *message) {
    raiseException(jniEnv, message, "com/r3/conclave/host/EnclaveLoadException");
}

void debug_print(const char *str, int n) {
    enclave_console(str, n);
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_getDeviceStatus
        (JNIEnv *, jobject) {
#ifdef SGX_SIM
    // If in simulation mode, simulate device capabilities.
    return SGX_ENABLED;
#else
    // Try to retrieve the current status of the SGX device.
    sgx_device_status_t status;
    sgx_status_t ret = sgx_cap_enable_device(&status);

    if (SGX_SUCCESS != ret) {
        return SGX_DISABLED;
    }

    return status;
#endif
}

jlong JNICALL Java_com_r3_conclave_host_internal_Native_createEnclave
        (JNIEnv *jniEnv, jobject, jstring enclavePath, jboolean isDebug) {
    JniString path(jniEnv, enclavePath);

    sgx_launch_token_t token = {0};
    sgx_enclave_id_t enclave_id = {0};
    int updated = 0;
    auto returnCode = sgx_create_enclave(path.c_str, isDebug, &token, &updated, &enclave_id, nullptr);
    if (returnCode == SGX_SUCCESS) {
        // Create the shared data pointer for the enclave
        r3::conclave::HostSharedData::instance().get(enclave_id);
        return enclave_id;
    } else {
        // Check to see if SGX is supported on the platform
        std::string message;
        bool wasEnabled = false;
        if (!checkAndEnableEnclaveSupport(true, wasEnabled, message)) {
            // SGX not enabled
            raiseEnclaveLoadException(jniEnv, message.c_str());
        }
        else {
            // SGX is enabled. If the function enabled it then attempt to load the enclave again
            if (wasEnabled) {
                returnCode = sgx_create_enclave(path.c_str, isDebug, &token, &updated, &enclave_id, nullptr);
                if (returnCode == SGX_SUCCESS) {
                    // Create the shared data pointer for the enclave
                    r3::conclave::HostSharedData::instance().get(enclave_id);
                    return enclave_id;
                }
                // Still failed. System may need a reboot
                raiseEnclaveLoadException(jniEnv, getDeviceStatusMessage(SGX_DISABLED_REBOOT_REQUIRED));
            }
            else {
                // Not a platform problem
                raiseEnclaveLoadException(jniEnv, getErrorMessage(returnCode));
            }
        }
        return -1;
    }
}

void JNICALL Java_com_r3_conclave_host_internal_Native_destroyEnclave
        (JNIEnv *jniEnv, jobject, jlong enclaveId) {
    if (!EcallContext::available()) {
        EcallContext _(static_cast<sgx_enclave_id_t>(enclaveId), jniEnv, {});
        const auto ret = ecall_finalize_enclave(static_cast<sgx_enclave_id_t>(enclaveId));
        if (ret != SGX_SUCCESS) {
            raiseException(jniEnv, getErrorMessage(ret));
        }
    } else {
        raiseException(jniEnv, "Enclave destruction not supported inside nested ecalls");
        return;
    }
    auto returnCode = sgx_destroy_enclave(static_cast<sgx_enclave_id_t>(enclaveId));
    if (returnCode != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(returnCode));
    }

    // Shutdown any shared data associated with this enclave
    r3::conclave::HostSharedData::instance().free(static_cast<sgx_enclave_id_t>(enclaveId));
}

void JNICALL Java_com_r3_conclave_host_internal_Native_jvmEcall
        (JNIEnv *jniEnv, jobject, jlong enclaveId, jbyteArray data)
try {
    // Prepare input buffer
    auto size = jniEnv->GetArrayLength(data);
    checkJniException(jniEnv);
    auto inputBuffer = jniEnv->GetByteArrayElements(data, nullptr);
    checkJniException(jniEnv);

    // Set the enclave ID TLS so that OCALLs have access to it
    EcallContext context(static_cast<sgx_enclave_id_t>(enclaveId), jniEnv, {});
    auto returnCode = jvm_ecall(
            static_cast<sgx_enclave_id_t>(enclaveId),
            inputBuffer,
            size
    );
    if (returnCode != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(returnCode));
    }
} catch (JNIException&) {
    // No-op: the host JVM will deal with it
}


typedef struct sgx_init_quote_request {
    sgx_target_info_t target_info;
    sgx_epid_group_id_t epid_group_id;
} sgx_init_quote_request_t;

JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_Native_initQuote
        (JNIEnv *jniEnv, jobject, jbyteArray initQuoteRequest) {
    JniPtr<sgx_init_quote_request_t> request(jniEnv, initQuoteRequest);
    auto returnCode = sgx_init_quote(&request.ptr->target_info, &request.ptr->epid_group_id);
    if (returnCode == SGX_SUCCESS) {
        request.releaseMode = 0;
    } else {
        raiseException(jniEnv, getErrorMessage(returnCode));
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_calcQuoteSize
        (JNIEnv *jniEnv, jobject, jbyteArray sigRlIn) {
    JniPtr<const uint8_t> sig_rl(jniEnv, sigRlIn);
    uint32_t quoteSize = 0;
    auto returnCode = sgx_calc_quote_size(
            sig_rl.ptr,
            static_cast<uint32_t>(sig_rl.size()),
            &quoteSize
    );
    if (returnCode == SGX_SUCCESS) {
        return quoteSize;
    } else {
        raiseException(jniEnv, getErrorMessage(returnCode));
        return -1;
    }
}

typedef struct sgx_get_quote_request {
    const sgx_report_t p_report;
    const sgx_quote_sign_type_t quote_type;
    const sgx_spid_t p_spid;
} sgx_get_quote_request_t;

JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_Native_getQuote
        (JNIEnv *jniEnv, jobject, jbyteArray getQuoteRequestIn, jbyteArray sigRlIn, jbyteArray qeReportNonceIn, jbyteArray qeReportOut, jbyteArray quoteOut) {
    JniPtr<const sgx_get_quote_request> request(jniEnv, getQuoteRequestIn);
    JniPtr<const uint8_t> sig_rl(jniEnv, sigRlIn);
    JniPtr<const sgx_quote_nonce_t> qe_report_nonce(jniEnv, qeReportNonceIn);
    JniPtr<sgx_report_t> qe_report(jniEnv, qeReportOut);
    JniPtr<sgx_quote_t> quote(jniEnv, quoteOut);
    auto returnCode = sgx_get_quote(
            &request.ptr->p_report,
            request.ptr->quote_type,
            &request.ptr->p_spid,
            qe_report_nonce.ptr,
            sig_rl.ptr,
            static_cast<uint32_t>(sig_rl.size()),
            qe_report.ptr,
            quote.ptr,
            static_cast<uint32_t>(quote.size())
    );
    if (returnCode == SGX_SUCCESS) {
        qe_report.releaseMode = 0;
        quote.releaseMode = 0;
    } else {
        raiseException(jniEnv, getErrorMessage(returnCode));
    }
}

JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_Native_getMetadata
        (JNIEnv *jniEnv, jobject, jstring enclaveFilePath, jbyteArray metadataOut) {

    JniString path(jniEnv, enclaveFilePath);
    JniPtr<metadata_t> metadata(jniEnv, metadataOut);

    auto status = retrieve_enclave_metadata(path.c_str, metadata.ptr);
    if (status != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(status));
        return;
    }
    metadata.releaseMode = 0;
}

void jvm_ocall(void* bufferIn, int bufferInLen) {
    auto *jniEnv = EcallContext::getJniEnv();
    if (jniEnv == nullptr) {
        throw std::runtime_error("Cannot find JNIEnv");
    }

    try {
        // Copy to a JVM buffer
        JniScopedRef<jbyteArray> javaBufferIn {jniEnv->NewByteArray(bufferInLen), jniEnv};
        checkJniException(jniEnv);
        jniEnv->SetByteArrayRegion(javaBufferIn.value(), 0, bufferInLen, static_cast<const jbyte *>(bufferIn));
        checkJniException(jniEnv);
        auto hostEnclaveApiClass = jniEnv->FindClass("com/r3/conclave/host/internal/NativeApi");
        checkJniException(jniEnv);
        auto jvmOcallMethodId = jniEnv->GetStaticMethodID(hostEnclaveApiClass, "enclaveToHost", "(J[B)V");
        checkJniException(jniEnv);
        jniEnv->CallStaticObjectMethod(hostEnclaveApiClass, jvmOcallMethodId, EcallContext::getEnclaveId(), javaBufferIn.value());
        checkJniException(jniEnv);
    } catch (JNIException&) {
      // No-op: delegate handling to the host JVM
    }
}

void* shared_data_ocall() {
    return static_cast<void*>(r3::conclave::HostSharedData::instance().get(EcallContext::getEnclaveId()));
}

static r3::conclave::dcap::QuotingAPI* quoting_lib = nullptr;
static std::mutex dcap_mutex;

jint initDCAP(JNIEnv *jniEnv, jstring bundle) {

    JniString jpath(jniEnv, bundle);

    if (quoting_lib != nullptr)
        return 0;

    r3::conclave::dcap::QuotingAPI::Errors errors;
    try {
        std::string path(std::string(jpath.c_str));

        quoting_lib = new r3::conclave::dcap::QuotingAPI();
        if ( !quoting_lib->init(path, errors) ){
            std::string message("failed to initialize DCAP: ");
            for(auto &err : errors)
                message += err + ";";

            raiseException(jniEnv, message.c_str());
            return -1;
        }
    }
    catch(...){
        quoting_lib = nullptr;

        raiseException(jniEnv, "failed to initialize DCAP: unknown error");
        return -1;
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_initQuoteDCAP
        (JNIEnv *jniEnv, jobject, jstring bundle, jbyteArray targetInfoOut) {

    JniPtr<sgx_target_info_t> request(jniEnv, targetInfoOut);

    std::lock_guard<std::mutex> lock(dcap_mutex);

    if (initDCAP(jniEnv, bundle) != 0)
        return -1;

    quote3_error_t eval_result;
    if (quoting_lib->get_target_info((sgx_target_info_t*)request.ptr, eval_result)) {
        request.releaseMode = 0;
        return 0;
    } else {
        raiseException(jniEnv, getQuotingErrorMessage(eval_result));
        return (int)eval_result;
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_calcQuoteSizeDCAP
        (JNIEnv *jniEnv, jobject) {

    std::lock_guard<std::mutex> lock(dcap_mutex);

    uint32_t quote_size;
    quote3_error_t eval_result;
    if (quoting_lib->get_quote_size(&quote_size, eval_result)) {
        return (jint)quote_size;
    } else {
        raiseException(jniEnv, getQuotingErrorMessage(eval_result));
        return (int)eval_result;
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_getQuoteDCAP
        (JNIEnv *jniEnv, jobject, jbyteArray getQuoteRequestIn, jbyteArray quoteOut) {

    JniPtr<const sgx_get_quote_request> request(jniEnv, getQuoteRequestIn);
    JniPtr<sgx_quote_t> quote(jniEnv, quoteOut);

    std::lock_guard<std::mutex> lock(dcap_mutex);

    quote3_error_t eval_result;
    if (quoting_lib->get_quote(const_cast<sgx_report_t*>(&request.ptr->p_report),
            static_cast<uint32_t>(quote.size()), (uint8_t*)quote.ptr, eval_result)) {
        quote.releaseMode = 0;
    } else {
        raiseException(jniEnv, getQuotingErrorMessage(eval_result));
    }

    return (int)eval_result;
}

JNIEXPORT jobjectArray JNICALL Java_com_r3_conclave_host_internal_Native_getQuoteCollateral
  (JNIEnv *jniEnv, jobject, jbyteArray fmspc, jint pck_ca_type) {

    JniPtr<uint8_t> p_fmspc(jniEnv, fmspc);

    std::lock_guard<std::mutex> lock(dcap_mutex);

    quote3_error_t eval_result;
    auto collateral = quoting_lib->get_quote_verification_collateral(p_fmspc.ptr, pck_ca_type, eval_result);
    if (collateral == nullptr){
        raiseException(jniEnv, getQuotingErrorMessage(eval_result));
        return nullptr;
    }
    else {
        jobjectArray arr= (jobjectArray)jniEnv->NewObjectArray(8,jniEnv->FindClass("java/lang/String"),nullptr);

    /**
        enum class PckCaType {
            Processor,
            Platform
        }
        enum class CollateralType {
            Version,
            PckCrlIssuerChain,
            RootCaCrl,
            PckCrl,
            TcbInfoIssuerChain,
            TcbInfo,
            QeIdentityIssuerChain,
            QeIdentity
        }
    */
        char version[2] = { '0', 0};
        version[0] += collateral->version;

        jniEnv->SetObjectArrayElement(arr,0,jniEnv->NewStringUTF(version));
        jniEnv->SetObjectArrayElement(arr,1,jniEnv->NewStringUTF(collateral->pck_crl_issuer_chain));
        jniEnv->SetObjectArrayElement(arr,2,jniEnv->NewStringUTF(collateral->root_ca_crl));
        jniEnv->SetObjectArrayElement(arr,3,jniEnv->NewStringUTF(collateral->pck_crl));
        jniEnv->SetObjectArrayElement(arr,4,jniEnv->NewStringUTF(collateral->tcb_info_issuer_chain));
        jniEnv->SetObjectArrayElement(arr,5,jniEnv->NewStringUTF(collateral->tcb_info));
        jniEnv->SetObjectArrayElement(arr,6,jniEnv->NewStringUTF(collateral->qe_identity_issuer_chain));
        jniEnv->SetObjectArrayElement(arr,7,jniEnv->NewStringUTF(collateral->qe_identity));

        return arr;
    }
}

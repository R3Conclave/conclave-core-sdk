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
#include "enclave_init.h"
#include <signal.h>
//  This is the file in the "fatfs/host" directory,
//    not the one in fatfs/enclave
#include "persistent_disk.hpp"
// TODO pool buffers in ecalls/ocalls

// From our patched version of the SGX SDK.
extern "C" void sgx_configure_thread_blocking(sgx_enclave_id_t enclave_id, uint64_t deadlock_timeout);

static void raiseEnclaveLoadException(JNIEnv *jniEnv, const char *message) {
    raiseException(jniEnv, message, "com/r3/conclave/host/EnclaveLoadException");
}

void debug_print_edl(const char *str, int n) {
    enclave_console(str, n);
}

static bool signal_registered = false;

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_getDeviceStatus(JNIEnv *, jclass) {
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

static void sigill_sigaction(int, siginfo_t *, void *) {
    std::cerr << "The enclave has aborted. Exiting.\n";
    exit(-1);
}

static void initialise_abort_handler() {
    // If an enclave aborts for any reason then the SGX SDK will signal this
    // using SIGILL. We cannot allow the host to continue when this happens but
    // rather than reporting SIGILL to the developer, log a more meaningful
    // message before exiting.
    if (!signal_registered) {
        signal_registered = true;
        struct sigaction sa;
        memset(&sa, 0, sizeof(struct sigaction));
        sigemptyset(&sa.sa_mask);
        sa.sa_sigaction = sigill_sigaction;
        sa.sa_flags   = SA_SIGINFO;
        sigaction(SIGILL, &sa, NULL);
    }

}

static void initialise_enclave(sgx_enclave_id_t enclave_id) {

    // Create the shared data pointer for the enclave
    r3::conclave::HostSharedData::instance().get(enclave_id);

    // Exchange configuration with the enclave
    r3::conclave::EnclaveInit ei;
    ecall_initialise_enclave(enclave_id, &ei, sizeof(ei));

    // We have patched the SGX SDK to automatically arbitrate threads and
    // handle deadlocks when there are more host threads calling into the 
    // enclave than there are TCS slots. Enable this now
    sgx_configure_thread_blocking(enclave_id, ei.deadlock_timeout_seconds);
}

jlong JNICALL Java_com_r3_conclave_host_internal_Native_createEnclave(JNIEnv *jniEnv,
                                                                      jclass,
                                                                      jstring enclavePath,
                                                                      jboolean isDebug) {

    initialise_abort_handler();
        
    JniString path(jniEnv, enclavePath);

    sgx_launch_token_t token = {0};
    sgx_enclave_id_t enclave_id = {0};
    int updated = 0;
    auto returnCode = sgx_create_enclave(path.c_str, isDebug, &token, &updated, &enclave_id, nullptr);
    if (returnCode == SGX_SUCCESS) {
        initialise_enclave(enclave_id);
        return enclave_id;
    } else {
        // The load might have failed due to SGX being disabled, in a way that we can auto-enable. But the user can
        // use the explicit API we provide to do this (it might require running as root, for example), so we just throw
        // here. We used to try and auto-enable on load but that's probably not quite right due to the permissions
        // issues, and it led to us accidentally hiding the true error when the attempt to enable failed as well.
        raiseEnclaveLoadException(jniEnv, getErrorMessage(returnCode));
        return -1;
    }
}

void JNICALL Java_com_r3_conclave_host_internal_Native_destroyEnclave(JNIEnv *jniEnv,
                                                                      jclass,
                                                                      jlong enclaveId) {
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

void JNICALL Java_com_r3_conclave_host_internal_Native_jvmECall(JNIEnv *jniEnv,
                                                                jclass,
                                                                jlong enclaveId,
                                                                jbyte callTypeID,
                                                                jbyte messageTypeID,
                                                                jbyteArray data) {
    try {
        // Prepare input buffer
        auto size = jniEnv->GetArrayLength(data);
        checkJniException(jniEnv);
        auto inputBuffer = jniEnv->GetByteArrayElements(data, nullptr);
        checkJniException(jniEnv);

        // Set the enclave ID TLS so that OCALLs have access to it
        EcallContext context(static_cast<sgx_enclave_id_t>(enclaveId), jniEnv, {});
        auto returnCode = jvm_ecall(static_cast<sgx_enclave_id_t>(enclaveId),
                                    callTypeID,
                                    messageTypeID,
                                    inputBuffer,
                                    size);
        jniEnv->ReleaseByteArrayElements(data, inputBuffer, 0);

        if (returnCode != SGX_SUCCESS) {
            raiseException(jniEnv, getErrorMessage(returnCode));
        }
    } catch (JNIException&) {
        // No-op: the host JVM will deal with it
    }
}

typedef struct sgx_init_quote_request {
    sgx_target_info_t target_info;
    sgx_epid_group_id_t epid_group_id;
} sgx_init_quote_request_t;

JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_Native_initQuote(JNIEnv *jniEnv,
                                                                           jclass,
                                                                           jbyteArray initQuoteRequest) {
    JniPtr<sgx_init_quote_request_t> request(jniEnv, initQuoteRequest);
    auto returnCode = sgx_init_quote(&request.ptr->target_info, &request.ptr->epid_group_id);
    if (returnCode == SGX_SUCCESS) {
        request.releaseMode = 0;
    } else {
        raiseException(jniEnv, getErrorMessage(returnCode));
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_calcQuoteSize(JNIEnv *jniEnv,
                                                                               jclass,
                                                                               jbyteArray sigRlIn) {
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

JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_Native_getQuote(JNIEnv *jniEnv,
                                                                          jclass,
                                                                          jbyteArray getQuoteRequestIn,
                                                                          jbyteArray sigRlIn,
                                                                          jbyteArray qeReportNonceIn,
                                                                          jbyteArray qeReportOut,
                                                                          jbyteArray quoteOut) {
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

JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_Native_getMetadata(JNIEnv *jniEnv,
                                                                             jclass,
                                                                             jstring enclaveFilePath,
                                                                             jbyteArray metadataOut) {

    JniString path(jniEnv, enclaveFilePath);
    JniPtr<metadata_t> metadata(jniEnv, metadataOut);

    auto status = retrieve_enclave_metadata(path.c_str, metadata.ptr);
    if (status != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(status));
        return;
    }
    metadata.releaseMode = 0;
}

void jvm_ocall(char callTypeID, char messageTypeID, void* data, int dataLengthBytes) {
    auto *jniEnv = EcallContext::getJniEnv();
    if (jniEnv == nullptr) {
        throw std::runtime_error("Cannot find JNIEnv");
    }

    try {
        // Wrap the native bytes in a Java direct byte buffer to avoid unnecessary copying. This is safe to do since the
        // memory is not de-allocated until after this function returns in
        // Java_com_r3_conclave_enclave_internal_Native_jvmOCall.
        auto javaBuffer = jniEnv->NewDirectByteBuffer(data, dataLengthBytes);
        checkJniException(jniEnv);
        auto hostEnclaveApiClass = jniEnv->FindClass("com/r3/conclave/host/internal/NativeApi");
        checkJniException(jniEnv);
        // enclaveToHost does not hold onto the direct byte buffer. Any bytes that need to linger after it returns are
        // copied from it. This means it's safe to de-allocate the pointer after this function returns.
        auto jvmOCallMethodId = jniEnv->GetStaticMethodID(hostEnclaveApiClass, "receiveOCall", "(JBBLjava/nio/ByteBuffer;)V");
        checkJniException(jniEnv);
        jniEnv->CallStaticObjectMethod(hostEnclaveApiClass, jvmOCallMethodId, EcallContext::getEnclaveId(), callTypeID, messageTypeID, javaBuffer);
        checkJniException(jniEnv);
    } catch (JNIException&) {
        // No-op: delegate handling to the host JVM
    }
}

// Called by the EDL when the enclave has decided to allocate the buffer on the untrusted stack
void jvm_ocall_stack(char callTypeID, char messageTypeID, void* data, int dataLengthBytes) {
    jvm_ocall(callTypeID, messageTypeID, data, dataLengthBytes);
}

// Called by the EDL when the enclave has decided to allocate the buffer on the hosts heap
void jvm_ocall_heap(char callTypeID, char messageTypeID, void* data, int dataLengthBytes) {
    jvm_ocall(callTypeID, messageTypeID, data, dataLengthBytes);
}

void shared_data_ocall(void** sharedBufferAddr) {
    void* shared_data = static_cast<void*>(r3::conclave::HostSharedData::instance().get(EcallContext::getEnclaveId()));
    *sharedBufferAddr = shared_data;
}

void allocate_untrusted_memory(void** untrustedBufferPtr, int size) {
    *untrustedBufferPtr = malloc(size);
}

void free_untrusted_memory(void** untrustedBufferPtr) {
    free(*untrustedBufferPtr);
}

void host_encrypted_read_ocall(int* res,
                               const unsigned char drive,
                               const unsigned long sector_id,
                               const unsigned char num_sectors,
                               const unsigned int sector_size,
                               unsigned char* buf,
                               const unsigned int buf_size) {
    const int res_f = host_disk_read(drive, sector_id, num_sectors, sector_size, buf);
    *res = res_f;
}


void host_encrypted_write_ocall(int* res,
                                const unsigned char drive,
                                const unsigned char* buf,
                                const unsigned int sector_size,
                                const unsigned long sector) {
    const int res_f = host_disk_write(drive, buf, sector_size, sector);
    *res = res_f;
}

void host_disk_get_size_ocall(long* res,
                              const unsigned char drive,
                              const unsigned long persistent_size) {
    const long res_f = host_disk_get_size(drive, persistent_size);
    *res = res_f;
}

// End OCalls for Persistent Filesystem

static r3::conclave::dcap::QuotingAPI* quoting_lib = nullptr;
static std::mutex dcap_mutex;

jint initDCAP(JNIEnv *jniEnv, jstring bundle, jboolean skipQuotingLibraries) {

    JniString jpath(jniEnv, bundle);

    if (quoting_lib != nullptr)
        return 0;

    r3::conclave::dcap::QuotingAPI::Errors errors;
    try {
        std::string path(std::string(jpath.c_str));

        quoting_lib = new r3::conclave::dcap::QuotingAPI();

        if (!quoting_lib->init(path, skipQuotingLibraries, errors)) {
            std::string message("failed to initialize DCAP: ");
            for(auto &err : errors)
                message += err + ";";

            delete quoting_lib;
            quoting_lib = nullptr;
            raiseException(jniEnv, message.c_str());
            return -1;
        }
    }
    catch(...){
        if (quoting_lib != nullptr) {
            delete quoting_lib;
            quoting_lib = nullptr;
        }

        raiseException(jniEnv, "failed to initialize DCAP: unknown error");
        return -1;
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_initQuoteDCAP(JNIEnv *jniEnv,
                                                                               jclass,
                                                                               jstring bundle,
                                                                               jboolean skipQuotingLibraries,
                                                                               jbyteArray targetInfoOut) {

    JniPtr<sgx_target_info_t> request(jniEnv, targetInfoOut);

    std::lock_guard<std::mutex> lock(dcap_mutex);

    if (initDCAP(jniEnv, bundle, skipQuotingLibraries) != 0) {
        return -1;
    }

    if (skipQuotingLibraries) {
        return 0;
    }

    quote3_error_t eval_result;
    if (quoting_lib->get_target_info((sgx_target_info_t*)request.ptr, eval_result)) {
        request.releaseMode = 0;
        return 0;
    } else {
        raiseException(jniEnv, getQuotingErrorMessage(eval_result));
        return (int)eval_result;
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_calcQuoteSizeDCAP(JNIEnv *jniEnv,
                                                                                   jclass) {

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

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_Native_getQuoteDCAP(JNIEnv *jniEnv,
                                                                              jclass,
                                                                              jbyteArray getQuoteRequestIn,
                                                                              jbyteArray quoteOut) {

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

JNIEXPORT jobjectArray JNICALL Java_com_r3_conclave_host_internal_Native_getQuoteCollateral(JNIEnv *jniEnv,
                                                                                            jclass,
                                                                                            jbyteArray fmspc,
                                                                                            jint pck_ca_type) {

    JniPtr<uint8_t> p_fmspc(jniEnv, fmspc);

    std::lock_guard<std::mutex> lock(dcap_mutex);

    quote3_error_t eval_result_get;
    auto collateral = quoting_lib->get_quote_verification_collateral(p_fmspc.ptr, pck_ca_type, eval_result_get);

    if (collateral == nullptr){
        raiseException(jniEnv, getQuotingErrorMessage(eval_result_get));
        return nullptr;
    } else {
        jobjectArray arr= (jobjectArray)jniEnv->NewObjectArray(8,jniEnv->FindClass("java/lang/Object"),nullptr);

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
        jclass integerClass = jniEnv->FindClass("java/lang/Integer");
        jmethodID integerConstructor = jniEnv->GetMethodID(integerClass, "<init>", "(I)V");
        jobject wrappedVersion = jniEnv->NewObject(integerClass, integerConstructor, static_cast<jint>(collateral->version));

        jniEnv->SetObjectArrayElement(arr,0,wrappedVersion);
        // TODO Convert the collateral fields to byte arrays, rather than Strings (which are converted back to bytes
        //      anyway in the Kotlin code)
        jniEnv->SetObjectArrayElement(arr,1,jniEnv->NewStringUTF(collateral->pck_crl_issuer_chain));
        jniEnv->SetObjectArrayElement(arr,2,jniEnv->NewStringUTF(collateral->root_ca_crl));
        jniEnv->SetObjectArrayElement(arr,3,jniEnv->NewStringUTF(collateral->pck_crl));
        jniEnv->SetObjectArrayElement(arr,4,jniEnv->NewStringUTF(collateral->tcb_info_issuer_chain));
        jniEnv->SetObjectArrayElement(arr,5,jniEnv->NewStringUTF(collateral->tcb_info));
        jniEnv->SetObjectArrayElement(arr,6,jniEnv->NewStringUTF(collateral->qe_identity_issuer_chain));
        jniEnv->SetObjectArrayElement(arr,7,jniEnv->NewStringUTF(collateral->qe_identity));
        
        quote3_error_t eval_result_free;
        
        if (!quoting_lib->free_quote_verification_collateral(eval_result_free)) {
            raiseException(jniEnv, getQuotingErrorMessage(eval_result_free));
            return nullptr;
        }           
        return arr;
    }
}

namespace r3::conclave {
    std::string getCpuCapabilitiesSummary();
}

JNIEXPORT jstring JNICALL Java_com_r3_conclave_host_internal_Native_getCpuCapabilitiesSummary(JNIEnv *jniEnv,
                                                                                              jclass) {
    return jniEnv->NewStringUTF(r3::conclave::getCpuCapabilitiesSummary().c_str());
}

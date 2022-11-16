#include <iostream>
#include <string>
#include <sys/mman.h>
#include <memory>
#include <mutex>
#include <signal.h>

#include "gramine_dcap.hpp"
#include "gramine_jni.hpp"
#include "jni_utils.hpp"

static std::shared_ptr<conclave::QuotingAPI> quoting_lib = nullptr;
static std::mutex dcap_mutex;

static std::string getQuotingErrorMessage(const int result) {
    return std::string("DCAP not initialized correctly: " + std::to_string(result));
}

jint initDCAP(JNIEnv *jniEnv, jstring bundle) {

    JniString jpath(jniEnv, bundle);

    if (quoting_lib != nullptr) {
        //  Already initialized
        return 0;
    }

    conclave::QuotingAPI::Errors errors;

    try {
        std::string path(std::string(jpath.c_str));

        quoting_lib = std::make_shared<conclave::QuotingAPI>();

        if (!quoting_lib->init(path, errors)) {
            std::string message("failed to initialize DCAP: ");

            for(auto &err : errors) {
                message += err + ";";
            }
            quoting_lib = nullptr;
            raiseException(jniEnv, message.c_str());
            return -1;
        }
    }
    catch(...) {
        if (quoting_lib != nullptr) {
            quoting_lib = nullptr;
        }

        raiseException(jniEnv, "failed to initialize DCAP: unknown error");
        return -1;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_host_internal_GramineNative_initQuoteDCAP(JNIEnv *jniEnv,
                                                                                    jclass,
                                                                                    jstring bundle) {
    printf("initQuoteDCAP\n");

    std::lock_guard<std::mutex> lock(dcap_mutex);

    if (initDCAP(jniEnv, bundle) != 0) {
        return -1;
    }
    printf("initQuoteDCAP 3\n");
    return 0;
}

JNIEXPORT jobjectArray JNICALL Java_com_r3_conclave_host_internal_GramineNative_getQuoteCollateral(JNIEnv *jniEnv,
                                                                                                 jclass,
                                                                                                 jbyteArray fmspc,
                                                                                                 jint pck_ca_type) {

    printf("getQuoteCollateral - 1\n");
    JniPtr<uint8_t> p_fmspc(jniEnv, fmspc);

    std::lock_guard<std::mutex> lock(dcap_mutex);
    printf("getQuoteCollateral - 2\n");

    quote3_error_t eval_result_get;
    const char* pck_ca = pck_ca_type == 1 ? "platform" : "processor";

    auto collateral = quoting_lib->get_quote_verification_collateral(p_fmspc.ptr, pck_ca, eval_result_get);
    printf("getQuoteCollateral - after\n");

    if (collateral == nullptr){
        raiseException(jniEnv, getQuotingErrorMessage(2).c_str());
        return nullptr;
    } else {
        jobjectArray arr= (jobjectArray)jniEnv->NewObjectArray(8,jniEnv->FindClass("java/lang/Object"),nullptr);


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
            raiseException(jniEnv, getQuotingErrorMessage(3).c_str());
            return nullptr;
        }           
        return arr;
    }
}

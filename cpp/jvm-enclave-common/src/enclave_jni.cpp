#include <jni.h>
#include <enclave_jni.h>
#include <jni_utils.h>
#include <os_support.h>
#include <jvm_t.h>
#include <singleton_jvm.h>
#include <dlsym_symbols.h>
#include <enclave_thread.h>
#include <aex_assert.h>

#include <sgx_eid.h>
#include <sgx_tseal.h>
#include <sgx_errors.h>
#include <sgx_trts.h>
#include <sgx_utils.h>
#include <se_memcpy.h>

#include <string>
#include <vector>
#include <cstdlib>
#include <algorithm>
#include <stdexcept>


namespace {

// TODO: Fix C++ exception catching in SGX and terminate with an exception instead of aborting
void abortOnJniException(JNIEnv *jniEnv) {
    if (jniEnv->ExceptionCheck() == JNI_TRUE) {
        jniEnv->ExceptionDescribe();
        abort();
    }
}

// Helper function to validate needed sealing structure.
bool validateArrayOffsetLength(JNIEnv* jniEnv, const jbyteArray arr, jint offset, jint size,
                               const std::string &fieldName) {
    if (size > 0) {
        if (!arr) {
            raiseException(jniEnv, ("invalid " + fieldName).c_str());
            return false;
        } else if (jniEnv->GetArrayLength(arr) < (offset + size)) {
            raiseException(jniEnv, (fieldName + " array too small").c_str());
            return false;
        }
    } else if (size < 0) {
        raiseException(jniEnv, (fieldName + " array has a negative size").c_str());
        return false;
    }
    return true;
}

// Checks the arguments passed on to sealing functions.
bool validateSealDataArgs
        (JNIEnv* jniEnv, const jbyteArray authenticatedData, jint authenticatedDataOffset,
         jint authenticatedDataSize,
         jbyteArray plaintext, jint plaintextOffset, jint plaintextSize,
         const jbyteArray output = nullptr, jint outputOffset = 0, jint outputLength = 0) {

    if (!validateArrayOffsetLength(jniEnv, plaintext, plaintextOffset, plaintextSize,
                                         "plaintext"))
        return false;

    if (authenticatedDataSize && !validateArrayOffsetLength(jniEnv, authenticatedData, authenticatedDataOffset,
                                                             authenticatedDataSize,
                                                             "authenticatedData"))
        return false;

    if (outputLength && !validateArrayOffsetLength(jniEnv, output, outputOffset, outputLength, "output"))
        return false;

    return true;
}

}

extern "C" {

extern const uint8_t _binary_app_jar_start[];
extern const uint8_t _binary_app_jar_end[];

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

JNIEXPORT jint JNICALL Java_com_r3_conclave_enclave_internal_Native_readAppJarChunk
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

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_randomBytes
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

JNIEXPORT jboolean JNICALL Java_com_r3_conclave_enclave_internal_Native_isEnclaveSimulation
        (JNIEnv *, jobject) {
#ifdef SGX_SIM
    return static_cast<jboolean>(true);
#else
    return static_cast<jboolean>(false);
#endif
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_enclave_internal_Native_calcSealedBlobSize
        (JNIEnv* jniEnv, jobject, jint plaintextSize, jint authenticatedDataSize) {

    auto ret = sgx_calc_sealed_data_size(authenticatedDataSize, plaintextSize);

    if (ret == UINT32_MAX) {
        raiseException(jniEnv, getErrorMessage(SGX_ERROR_UNEXPECTED));
        return -1;
    } else {
        return static_cast<jint>(ret);
    }
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_enclave_internal_Native_authenticatedDataSize
        (JNIEnv* jniEnv, jobject, jbyteArray sealedBlob) {
    JniPtr<uint8_t> jpSealedBlob(jniEnv, sealedBlob);

    if (jpSealedBlob.ptr) {
        auto ret = sgx_get_add_mac_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jpSealedBlob.ptr));

        if (ret != UINT32_MAX) {
            return static_cast<jint>(ret);
        }
    }
    raiseException(jniEnv, getErrorMessage(SGX_ERROR_UNEXPECTED));
    return -1;
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_enclave_internal_Native_plaintextSizeFromSealedData
        (JNIEnv* jniEnv, jobject, jbyteArray sealedBlob) {

    JniPtr<uint8_t> jpSealedBlob(jniEnv, sealedBlob);

    if (jpSealedBlob.ptr) {
        auto ret = sgx_get_encrypt_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jpSealedBlob.ptr));

        if (ret != UINT32_MAX) {
            return static_cast<jint>(ret);
        }
    }
    raiseException(jniEnv, getErrorMessage(SGX_ERROR_UNEXPECTED));
    return -1;
}

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_sealData
        (JNIEnv* jniEnv, jobject,
         jbyteArray output, jint outputOffset, jint outputSize,
         jbyteArray plaintext, jint plaintextOffset, jint plaintextSize,
         jbyteArray authenticatedData, jint authenticatedDataOffset, jint authenticatedDataSize) {

    if (!validateSealDataArgs(jniEnv, authenticatedData, authenticatedDataOffset, authenticatedDataSize,
                              plaintext, plaintextOffset, plaintextSize, output, outputOffset,
                              outputSize)) {
        raiseException(jniEnv, "sealData arguments validation failure");
        return;
    }

    const auto sealedDataSize = sgx_calc_sealed_data_size(authenticatedDataSize, plaintextSize);

    // Check if the output can fit the provided data.
    if (static_cast<std::remove_const<decltype(sealedDataSize)>::type>(outputSize) < sealedDataSize) {
        std::string msg = "output (size " + std::to_string(outputSize) + ") can't fit sealed data (size " +
                          std::to_string(sealedDataSize) + ")";
        raiseException(jniEnv, msg.c_str());
        return;
    }

    try {
        std::vector <uint8_t> buffer(sealedDataSize);

        JniPtr<uint8_t> jpDataToEncrypt(jniEnv, plaintext);
        JniPtr<uint8_t> jpAuthenticatedData(jniEnv, authenticatedData);

        sgx_status_t ret = jpDataToEncrypt.ptr ? sgx_seal_data(authenticatedDataSize,
                                                         reinterpret_cast<const uint8_t*>(jpAuthenticatedData.ptr),
                                                         plaintextSize,
                                                         reinterpret_cast<const uint8_t*>(jpDataToEncrypt.ptr),
                                                         sealedDataSize,
                                                         reinterpret_cast<sgx_sealed_data_t*>(&buffer[0]))
                                         : SGX_ERROR_UNEXPECTED;

        if (ret == SGX_SUCCESS) {
            JniPtr<uint8_t> jpSealedOutput(jniEnv, output);
            memcpy_s(jpSealedOutput.ptr + outputOffset, outputSize, &buffer[0], buffer.size());
            jpSealedOutput.releaseMode = 0; // to write back to the jvm
        } else {
            raiseException(jniEnv, getErrorMessage(ret));
        }
    } catch (std::exception &e) {
        raiseException(jniEnv, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_unsealData
        (JNIEnv* jniEnv, jobject,
         jbyteArray sealedBlob, jint sealedBlobOffset, jint sealedBlobLength,
         jbyteArray dataOut, jint dataOutOffset, jint dataOutLength,
         jbyteArray authenticatedDataOut, jint authenticatedDataOutOffset, jint authenticatedDataOutLength) {

    JniPtr<uint8_t> jpSealedBlob(jniEnv, sealedBlob);
    JniPtr<uint8_t> jpDataOut(jniEnv, dataOut);

    auto authenticatedDataOutDataLen = sgx_get_add_mac_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jpSealedBlob.ptr));
    auto decryptDataLen = sgx_get_encrypt_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jpSealedBlob.ptr));

    if (authenticatedDataOutDataLen == UINT32_MAX || decryptDataLen == UINT32_MAX) {
        raiseException(jniEnv, getErrorMessage(SGX_ERROR_UNEXPECTED));
        return;
    }

    auto uiSealedBlobLength = static_cast<uint64_t>(sealedBlobLength);

    // Lambda helper to validate parameters. Returns true in case something is invalid.
    auto validateParameter = [](const JniPtr<uint8_t>& jniPtr, int offset, int length) {
        return jniPtr.ptr == nullptr
            || offset < 0 
            || length <= 0
            || offset >= length
            || (static_cast<uint64_t>(offset) + static_cast<uint64_t>(length)) > static_cast<uint64_t>(jniPtr.size()); 
            };

    if (validateParameter(jpSealedBlob, sealedBlobOffset, sealedBlobLength)
     || validateParameter(jpDataOut, dataOutOffset, dataOutLength) 
     || (static_cast<uint64_t>(authenticatedDataOutDataLen) + static_cast<uint64_t>(decryptDataLen)) > uiSealedBlobLength) {
        raiseException(jniEnv, getErrorMessage(SGX_ERROR_INVALID_PARAMETER));
        return;
    }

    try {
        std::vector<uint8_t> deAuthenticatedData(authenticatedDataOutDataLen);
        std::vector<uint8_t> deData(decryptDataLen);

        auto res = sgx_unseal_data(reinterpret_cast<sgx_sealed_data_t*>(jpSealedBlob.ptr + sealedBlobOffset),
                                   authenticatedDataOutDataLen ? &deAuthenticatedData[0] : nullptr, 
                                   authenticatedDataOutDataLen ? &authenticatedDataOutDataLen : nullptr, 
                                   &deData[0], &decryptDataLen);
        if (res != SGX_SUCCESS) {
            raiseException(jniEnv, getErrorMessage(res));
            return;
        }

        if (authenticatedDataOutLength) {
            auto len = std::min<int>(authenticatedDataOutLength, authenticatedDataOutDataLen);
            JniPtr<uint8_t> jpAuthenticatedDataOut(jniEnv, authenticatedDataOut);
            if (validateParameter(jpAuthenticatedDataOut, authenticatedDataOutOffset, len)) {
                raiseException(jniEnv, getErrorMessage(SGX_ERROR_INVALID_PARAMETER));
                return;
            }
            memcpy_s(jpAuthenticatedDataOut.ptr + authenticatedDataOutOffset, 
                     len,
                     &deAuthenticatedData[0], deAuthenticatedData.size());
            // to write back to the jvm
            jpAuthenticatedDataOut.releaseMode = 0;
        }

        memcpy_s(jpDataOut.ptr + dataOutOffset, dataOutLength, &deData[0], deData.size());

        // to write back to the jvm
        jpDataOut.releaseMode = 0;
    } catch (std::exception &e) {
        raiseException(jniEnv, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_getKey
        (JNIEnv* jniEnv, jobject, jbyteArray keyRequestIn, jbyteArray keyOut) {
    auto *key_request = jniEnv->GetByteArrayElements(keyRequestIn, nullptr);
    auto *key = jniEnv->GetByteArrayElements(keyOut, nullptr);

    auto return_code = sgx_get_key(
            reinterpret_cast<const sgx_key_request_t *>(key_request),
            reinterpret_cast<sgx_key_128bit_t *>(key)
    );

    jniEnv->ReleaseByteArrayElements(keyRequestIn, key_request, JNI_ABORT);
    if (return_code != SGX_SUCCESS) {
        jniEnv->ReleaseByteArrayElements(keyOut, key, JNI_ABORT);
        raiseException(jniEnv, getErrorMessage(return_code));
    } else {
        jniEnv->ReleaseByteArrayElements(keyOut, key, 0);
    }
}

DLSYM_STATIC {
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_jvmOcall);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_readAppJarChunk);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_createReport);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_randomBytes);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_isEnclaveSimulation);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_sealData);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_unsealData);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_calcSealedBlobSize);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_authenticatedDataSize);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_plaintextSizeFromSealedData);
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_getKey);
};
}

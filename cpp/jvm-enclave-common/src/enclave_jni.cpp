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

    jbyte* jbSealedBlob = sealedBlob ? jniEnv->GetByteArrayElements(sealedBlob, nullptr) : nullptr;

    if (jbSealedBlob) {
        auto ret = sgx_get_add_mac_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jbSealedBlob));

        if (ret != UINT32_MAX) {
            return static_cast<jint>(ret);
        }
    }
    raiseException(jniEnv, getErrorMessage(SGX_ERROR_UNEXPECTED));
    return -1;
}

JNIEXPORT jint JNICALL Java_com_r3_conclave_enclave_internal_Native_plaintextSizeFromSealedData
        (JNIEnv* jniEnv, jobject, jbyteArray sealedBlob) {

    jbyte* jbSealedBlob = sealedBlob ? jniEnv->GetByteArrayElements(sealedBlob, nullptr) : nullptr;

    if (jbSealedBlob) {
        auto ret = sgx_get_encrypt_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jbSealedBlob));

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
    if (static_cast<decltype(sealedDataSize)>(outputSize) < sealedDataSize) {
        std::string msg = "output (size " + std::to_string(outputSize) + ") can't fit sealed data (size " +
                          std::to_string(sealedDataSize) + ")";
        raiseException(jniEnv, msg.c_str());
        return;
    }

    try {
        std::vector <uint8_t> buffer(sealedDataSize);

        jbyte* jbDataToEncrypt = plaintext ?
                               jniEnv->GetByteArrayElements(plaintext, nullptr) : nullptr;
        jbyte* jbAuthenticatedData = authenticatedData ?
                                   jniEnv->GetByteArrayElements(authenticatedData, nullptr) : nullptr;

        sgx_status_t ret = jbDataToEncrypt ? sgx_seal_data(authenticatedDataSize,
                                                         reinterpret_cast<const uint8_t*>(jbAuthenticatedData),
                                                         plaintextSize,
                                                         reinterpret_cast<const uint8_t*>(jbDataToEncrypt),
                                                         sealedDataSize,
                                                         reinterpret_cast<sgx_sealed_data_t*>(&buffer[0]))
                                         : SGX_ERROR_UNEXPECTED;

        if (ret == SGX_SUCCESS) {
            JniPtr<uint8_t> sealedOutput(jniEnv, output);
            memcpy_s(sealedOutput.ptr + outputOffset, outputSize, &buffer[0], buffer.size());
            sealedOutput.releaseMode = 0; // to write back to the jvm
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

    jbyte* jbSealedBlob = sealedBlob ? jniEnv->GetByteArrayElements(sealedBlob, nullptr) : nullptr;

    auto authenticatedDataOutDataLen = sgx_get_add_mac_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jbSealedBlob));
    auto decryptDataLen = sgx_get_encrypt_txt_len(reinterpret_cast<sgx_sealed_data_t*>(jbSealedBlob));

    if (authenticatedDataOutDataLen == UINT32_MAX || decryptDataLen == UINT32_MAX) {
        raiseException(jniEnv, getErrorMessage(SGX_ERROR_UNEXPECTED));
        return;
    }

    auto uiSealedBlobLength = static_cast<uint32_t>(sealedBlobLength);

    if ((authenticatedDataOutDataLen + decryptDataLen) > uiSealedBlobLength) {
        raiseException(jniEnv, getErrorMessage(SGX_ERROR_INVALID_PARAMETER));
        return;
    }

    try {
        std::vector <uint8_t> deAuthenticatedData(authenticatedDataOutDataLen);
        std::vector <uint8_t> deData(decryptDataLen);

        auto res = sgx_unseal_data(reinterpret_cast<sgx_sealed_data_t*>(jbSealedBlob + sealedBlobOffset),
                                   authenticatedDataOutDataLen ? &deAuthenticatedData[0] : nullptr, authenticatedDataOutDataLen ? &authenticatedDataOutDataLen : nullptr, &deData[0], &decryptDataLen);
        if (res != SGX_SUCCESS) {
            raiseException(jniEnv, getErrorMessage(res));
            return;
        }

        if (authenticatedDataOutLength) {
            JniPtr<uint8_t> jpAuthenticatedDataOut(jniEnv, authenticatedDataOut);
            memcpy_s(jpAuthenticatedDataOut.ptr + authenticatedDataOutOffset, authenticatedDataOutLength,
                     &deAuthenticatedData[0], deAuthenticatedData.size());
            // to write back to the jvm
            jpAuthenticatedDataOut.releaseMode = 0;
        }

        JniPtr<uint8_t> jpDataOut(jniEnv, dataOut);
        memcpy_s(jpDataOut.ptr + dataOutOffset, dataOutLength, &deData[0], deData.size());

        // to write back to the jvm
        jpDataOut.releaseMode              = 0;
    } catch (std::exception &e) {
        raiseException(jniEnv, e.what());
    }
}

JNIEXPORT void JNICALL Java_com_r3_conclave_enclave_internal_Native_sgxKey
        (JNIEnv* jniEnv, jobject,
         jint keyType, jint keyPolicy, jbyteArray keyOut, jint keyOutOffset, jint keyOutSize) {

    const auto ui16KeyType      = static_cast<uint16_t>(keyType);
    const auto ui16KeyPolicy    = static_cast<uint16_t>(keyPolicy);
    const auto ui32KeyOutSize   = static_cast<uint32_t>(keyOutSize);
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

    req.key_name    = ui16KeyType;
    req.key_policy  = ui16KeyPolicy;

    auto res = sgx_get_key(&req, &key);

    if (res != SGX_SUCCESS) {
        raiseException(jniEnv, getErrorMessage(res));
        return;
    }

    JniPtr<uint8_t> jpKeyOut(jniEnv, keyOut);
    memcpy_s(jpKeyOut.ptr + keyOutOffset, keyOutSize, key, sizeof(key));

    jpKeyOut.releaseMode = 0; // to write back to the jvm
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
    DLSYM_ADD(Java_com_r3_conclave_enclave_internal_Native_sgxKey);
};
}

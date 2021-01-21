#include <sgx_errors.h>
#include <map>

#include <sgx_ql_lib_common.h>
#include <sgx_qve_header.h>

namespace {
    /* Error code returned by sgx_create_enclave */
    const std::map<sgx_status_t, const char *>& getErrors();

    /* DCAP error codes */
    const std::map<quote3_error_t, const char *>& getQuotingErrors();
    const std::map<sgx_ql_qv_result_t, const char *>& getQuoteVerificationErrors();
}

const char* getErrorMessage(sgx_status_t status) {
    const auto& sgxErrors = getErrors();
    auto iter = sgxErrors.find(status);
    if (iter == sgxErrors.end()) {
        return "Unknown error code";
    } else {
        return iter->second;
    }
}

const char* getQuotingErrorMessage(uint32_t status) {
    const uint32_t generation_error_mask = 0x0000E000;
    const uint32_t verification_error_mask = 0x0000A000;

    if ((status & generation_error_mask)  == generation_error_mask)
        return getQuoteGenerationErrorMessage((quote3_error_t)status);

    if ((status & verification_error_mask)  == verification_error_mask)
        return getQuoteVerificationErrorMessage((sgx_ql_qv_result_t)status);

    return getErrorMessage((sgx_status_t)status);
}

const char* getQuoteGenerationErrorMessage(quote3_error_t status) {
    const auto& sgxErrors = getQuotingErrors();
    auto iter = sgxErrors.find(status);
    if (iter == sgxErrors.end()) {
        return "Unknown error code";
    } else {
        return iter->second;
    }
}

const char* getQuoteVerificationErrorMessage(sgx_ql_qv_result_t status) {
    const auto& sgxErrors = getQuoteVerificationErrors();
    auto iter = sgxErrors.find(status);
    if (iter == sgxErrors.end()) {
        return "Unknown error code";
    } else {
        return iter->second;
    }
}

namespace {
#define Q3ERR(c,s) {c, #c ": " s}

    const std::map<sgx_status_t, const char *>& getErrors() {
        static const std::map<sgx_status_t, const char *> error_map {
            Q3ERR(SGX_ERROR_UNEXPECTED, "Unexpected error"),
            Q3ERR(SGX_ERROR_INVALID_PARAMETER, "The parameter is incorrect"),
            Q3ERR(SGX_ERROR_OUT_OF_MEMORY, "Not enough memory is available to complete this operation"),
            Q3ERR(SGX_ERROR_ENCLAVE_LOST, "Enclave lost after power transition or used in child process created by linux:fork()" ),
            Q3ERR(SGX_ERROR_INVALID_STATE, "SGX API is invoked in incorrect order or state" ),
            Q3ERR(SGX_ERROR_INVALID_FUNCTION, "The ecall/ocall index is invalid" ),
            Q3ERR(SGX_ERROR_OUT_OF_TCS, "The enclave is out of TCS" ),
            Q3ERR(SGX_ERROR_ENCLAVE_CRASHED, "The enclave has crashed"),
            Q3ERR(SGX_ERROR_ECALL_NOT_ALLOWED, "The ECALL is not allowed at this time, e.g. ecall is blocked by the dynamic entry table, or nested ecall is not allowed during initialization" ),
            Q3ERR(SGX_ERROR_OCALL_NOT_ALLOWED, "The OCALL is not allowed at this time, e.g. ocall is not allowed during exception handling" ),
            Q3ERR(SGX_ERROR_STACK_OVERRUN, "The enclave is running out of stack" ),
            Q3ERR(SGX_ERROR_UNDEFINED_SYMBOL, "The enclave image has undefined symbol." ),
            Q3ERR(SGX_ERROR_INVALID_ENCLAVE, "The enclave image is not correct." ),
            Q3ERR(SGX_ERROR_INVALID_ENCLAVE_ID, "The enclave id is invalid" ),
            Q3ERR(SGX_ERROR_INVALID_SIGNATURE, "The signature is invalid" ),
            Q3ERR(SGX_ERROR_NDEBUG_ENCLAVE, "The enclave is signed as product enclave, and can not be created as debuggable enclave." ),
            Q3ERR(SGX_ERROR_OUT_OF_EPC, "Not enough EPC is available to load the enclave" ),
            Q3ERR(SGX_ERROR_NO_DEVICE, "Can't open SGX device. Ensure the SGX platform software is installed. If SGX has been software enabled a reboot may be required" ),
            Q3ERR(SGX_ERROR_MEMORY_MAP_CONFLICT, "Page mapping failed in driver" ),
            Q3ERR(SGX_ERROR_INVALID_METADATA, "The metadata is incorrect." ),
            Q3ERR(SGX_ERROR_DEVICE_BUSY, "Device is busy, mostly EINIT failed." ),
            Q3ERR(SGX_ERROR_INVALID_VERSION, "Metadata version is inconsistent between uRTS and sgx_sign or uRTS is incompatible with current platform." ),
            Q3ERR(SGX_ERROR_MODE_INCOMPATIBLE, "The target enclave 32/64 bit mode or sim/hw mode is incompatible with the mode of current uRTS." ),
            Q3ERR(SGX_ERROR_ENCLAVE_FILE_ACCESS, "Can't open enclave file." ),
            Q3ERR(SGX_ERROR_INVALID_MISC, "The MiscSelct/MiscMask settings are not correct" ),
            Q3ERR(SGX_ERROR_INVALID_LAUNCH_TOKEN, "The launch token is not correct" ),
            Q3ERR(SGX_ERROR_MAC_MISMATCH, "Indicates verification error for reports, sealed datas, etc" ),
            Q3ERR(SGX_ERROR_INVALID_ATTRIBUTE, "The enclave is not authorized" ),
            Q3ERR(SGX_ERROR_INVALID_CPUSVN, "The cpu svn is beyond platform's cpu svn value" ),
            Q3ERR(SGX_ERROR_INVALID_ISVSVN, "The isv svn is greater than the enclave's isv svn" ),
            Q3ERR(SGX_ERROR_INVALID_KEYNAME, "The key name is an unsupported value" ),
            Q3ERR(SGX_ERROR_SERVICE_UNAVAILABLE, "Indicates aesm didn't respond or the requested service is not supported" ),
            Q3ERR(SGX_ERROR_SERVICE_TIMEOUT, "The request to aesm timed out" ),
            Q3ERR(SGX_ERROR_AE_INVALID_EPIDBLOB, "Indicates epid blob verification error" ),
            Q3ERR(SGX_ERROR_SERVICE_INVALID_PRIVILEGE, "Enclave has no privilege to get launch token" ),
            Q3ERR(SGX_ERROR_EPID_MEMBER_REVOKED, "The EPID group membership is revoked." ),
            Q3ERR(SGX_ERROR_UPDATE_NEEDED, "SGX needs to be updated" ),
            Q3ERR(SGX_ERROR_NETWORK_FAILURE, "Network connecting or proxy setting issue is encountered" ),
            Q3ERR(SGX_ERROR_AE_SESSION_INVALID, "Session is invalid or ended by server" ),
            Q3ERR(SGX_ERROR_BUSY, "The requested service is temporarily not availabe" ),
            Q3ERR(SGX_ERROR_MC_NOT_FOUND, "The Monotonic Counter doesn't exist or has been invalided" ),
            Q3ERR(SGX_ERROR_MC_NO_ACCESS_RIGHT, "Caller doesn't have the access right to specified VMC" ),
            Q3ERR(SGX_ERROR_MC_USED_UP, "Monotonic counters are used out" ),
            Q3ERR(SGX_ERROR_MC_OVER_QUOTA, "Monotonic counters exceeds quota limitation" ),
            Q3ERR(SGX_ERROR_KDF_MISMATCH, "Key derivation function doesn't match during key exchange" ),
            Q3ERR(SGX_ERROR_UNRECOGNIZED_PLATFORM, "EPID Provisioning failed due to platform not recognized by backend server" ),
            Q3ERR(SGX_ERROR_NO_PRIVILEGE, "Not enough privilege to perform the operation" ),
            Q3ERR(SGX_ERROR_PCL_ENCRYPTED, "trying to encrypt an already encrypted enclave" ),
            Q3ERR(SGX_ERROR_PCL_NOT_ENCRYPTED, "trying to load a plain enclave using sgx_create_encrypted_enclave" ),
            Q3ERR(SGX_ERROR_PCL_MAC_MISMATCH, "section mac result does not match build time mac" ),
            Q3ERR(SGX_ERROR_PCL_SHA_MISMATCH, "Unsealed key MAC does not match MAC of key hardcoded in enclave binary" ),
            Q3ERR(SGX_ERROR_PCL_GUID_MISMATCH, "GUID in sealed blob does not match GUID hardcoded in enclave binary" ),
            Q3ERR(SGX_ERROR_FILE_BAD_STATUS, "The file is in bad status, run sgx_clearerr to try and fix it" ),
            Q3ERR(SGX_ERROR_FILE_NO_KEY_ID, "The Key ID field is all zeros, can't re-generate the encryption key" ),
            Q3ERR(SGX_ERROR_FILE_NAME_MISMATCH, "The current file name is different then the original file name (not allowed, substitution attack)"),
            Q3ERR(SGX_ERROR_FILE_NOT_SGX_FILE, "The file is not an SGX file"),
            Q3ERR(SGX_ERROR_FILE_CANT_OPEN_RECOVERY_FILE, "A recovery file can't be opened, so flush operation can't continue (only used when no EXXX is returned) "),
            Q3ERR(SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE, "A recovery file can't be written, so flush operation can't continue (only used when no EXXX is returned) "),
            Q3ERR(SGX_ERROR_FILE_RECOVERY_NEEDED, "When openeing the file, recovery is needed, but the recovery process failed"),
            Q3ERR(SGX_ERROR_FILE_FLUSH_FAILED, "fflush operation (to disk) failed (only used when no EXXX is returned)"),
            Q3ERR(SGX_ERROR_FILE_CLOSE_FAILED, "fclose operation (to disk) failed (only used when no EXXX is returned)")
        };
        return error_map;
    };

    const std::map<quote3_error_t, const char *>& getQuotingErrors() {
        /// (0x0000E000|(x))
        static const std::map<quote3_error_t, const char *> error_map {
            Q3ERR( SGX_QL_ERROR_UNEXPECTED, "Unexpected error"),
            Q3ERR( SGX_QL_ERROR_INVALID_PARAMETER, "The parameter is incorrect"),
            Q3ERR( SGX_QL_ERROR_OUT_OF_MEMORY, "Not enough memory is available to complete this operation"),
            Q3ERR( SGX_QL_ERROR_ECDSA_ID_MISMATCH, "Expected ECDSA_ID does not match the value stored in the ECDSA Blob"),
            Q3ERR( SGX_QL_PATHNAME_BUFFER_OVERFLOW_ERROR, "The ECDSA blob pathname is too large"),
            Q3ERR( SGX_QL_FILE_ACCESS_ERROR, "Error accessing ECDSA blob"),
            Q3ERR( SGX_QL_ERROR_STORED_KEY, "Cached ECDSA key is invalid"),
            Q3ERR( SGX_QL_ERROR_PUB_KEY_ID_MISMATCH, "Cached ECDSA key does not match requested key"),
            Q3ERR( SGX_QL_ERROR_INVALID_PCE_SIG_SCHEME, "PCE use the incorrect signature scheme"),
            Q3ERR( SGX_QL_ATT_KEY_BLOB_ERROR, "There is a problem with the attestation key blob"),
            Q3ERR( SGX_QL_UNSUPPORTED_ATT_KEY_ID, "Unsupported attestation key ID"),
            Q3ERR( SGX_QL_UNSUPPORTED_LOADING_POLICY, "Unsupported enclave loading policy"),
            Q3ERR( SGX_QL_INTERFACE_UNAVAILABLE, "Unable to load the QE enclave"),
            Q3ERR( SGX_QL_PLATFORM_LIB_UNAVAILABLE, "Unable to find the platform library with the dependent APIs. Not fatal"),
            Q3ERR( SGX_QL_ATT_KEY_NOT_INITIALIZED, "The attestation key doesn't exist or has not been certified"),
            Q3ERR( SGX_QL_ATT_KEY_CERT_DATA_INVALID, "The certification data retrieved from the platform library is invalid"),
            Q3ERR( SGX_QL_NO_PLATFORM_CERT_DATA, "The platform library doesn't have any platfrom cert data"),
            Q3ERR( SGX_QL_OUT_OF_EPC, "Not enough memory in the EPC to load the enclave"),
            Q3ERR( SGX_QL_ERROR_REPORT, "There was a problem verifying an SGX REPORT"),
            Q3ERR( SGX_QL_ENCLAVE_LOST, "Interfacing to the enclave failed due to a power transition"),
            Q3ERR( SGX_QL_INVALID_REPORT, "Error verifying the application enclave's report"),
            Q3ERR( SGX_QL_ENCLAVE_LOAD_ERROR, "Unable to load the enclaves. Could be due to file I/O error, loading infrastructure error"),
            Q3ERR( SGX_QL_UNABLE_TO_GENERATE_QE_REPORT, "The QE was unable to generate its own report targeting the application enclave either because the QE doesn't support this feature there is an enclave compatibility issue. Please call again with the p_qe_report_info to NULL"),

            Q3ERR( SGX_QL_KEY_CERTIFCATION_ERROR, "Caused when the provider library returns an invalid TCB (too high)"),
            Q3ERR( SGX_QL_NETWORK_ERROR, "Network error when retrieving PCK certs"),
            Q3ERR( SGX_QL_MESSAGE_ERROR, "Message error when retrieving PCK certs"),
            Q3ERR( SGX_QL_NO_QUOTE_COLLATERAL_DATA, "The platform does not have the quote verification collateral data available"),
            Q3ERR( SGX_QL_QUOTE_CERTIFICATION_DATA_UNSUPPORTED, ""),
            Q3ERR( SGX_QL_QUOTE_FORMAT_UNSUPPORTED, ""),
            Q3ERR( SGX_QL_UNABLE_TO_GENERATE_REPORT, ""),
            Q3ERR( SGX_QL_QE_REPORT_INVALID_SIGNATURE, ""),
            Q3ERR( SGX_QL_QE_REPORT_UNSUPPORTED_FORMAT, ""),
            Q3ERR( SGX_QL_PCK_CERT_UNSUPPORTED_FORMAT, ""),
            Q3ERR( SGX_QL_PCK_CERT_CHAIN_ERROR, ""),
            Q3ERR( SGX_QL_TCBINFO_UNSUPPORTED_FORMAT, ""),
            Q3ERR( SGX_QL_TCBINFO_MISMATCH, ""),
            Q3ERR( SGX_QL_QEIDENTITY_UNSUPPORTED_FORMAT, ""),
            Q3ERR( SGX_QL_QEIDENTITY_MISMATCH, ""),
            Q3ERR( SGX_QL_TCB_OUT_OF_DATE, ""),
            Q3ERR( SGX_QL_TCB_OUT_OF_DATE_CONFIGURATION_NEEDED, "TCB out of date and Configuration needed"),
            Q3ERR( SGX_QL_SGX_ENCLAVE_IDENTITY_OUT_OF_DATE, ""),
            Q3ERR( SGX_QL_SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE, ""),
            Q3ERR( SGX_QL_QE_IDENTITY_OUT_OF_DATE, ""),
            Q3ERR( SGX_QL_SGX_TCB_INFO_EXPIRED, ""),
            Q3ERR( SGX_QL_SGX_PCK_CERT_CHAIN_EXPIRED, ""),
            Q3ERR( SGX_QL_SGX_CRL_EXPIRED, ""),
            Q3ERR( SGX_QL_SGX_SIGNING_CERT_CHAIN_EXPIRED, ""),
            Q3ERR( SGX_QL_SGX_ENCLAVE_IDENTITY_EXPIRED, ""),
            Q3ERR( SGX_QL_PCK_REVOKED, ""),
            Q3ERR( SGX_QL_TCB_REVOKED, ""),
            Q3ERR( SGX_QL_TCB_CONFIGURATION_NEEDED, ""),
            Q3ERR( SGX_QL_UNABLE_TO_GET_COLLATERAL, ""),
            Q3ERR( SGX_QL_ERROR_INVALID_PRIVILEGE, "No enough privilege to perform the operation"),
            Q3ERR( SGX_QL_NO_QVE_IDENTITY_DATA, "The platform does not have the QVE identity data available"),
            Q3ERR( SGX_QL_CRL_UNSUPPORTED_FORMAT, ""),
            Q3ERR( SGX_QL_QEIDENTITY_CHAIN_ERROR, ""),
            Q3ERR( SGX_QL_TCBINFO_CHAIN_ERROR, ""),
            Q3ERR( SGX_QL_ERROR_QVL_QVE_MISMATCH, "QvE returned supplemental data version mismatched between QVL and QvE"),
            Q3ERR( SGX_QL_TCB_SW_HARDENING_NEEDED, "TCB up to date but SW Hardening needed"),
            Q3ERR( SGX_QL_TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED, "TCB up to date but Configuration and SW Hardening needed"),

            Q3ERR( SGX_QL_UNSUPPORTED_MODE, ""),
            Q3ERR( SGX_QL_NO_DEVICE, ""),
            Q3ERR( SGX_QL_SERVICE_UNAVAILABLE, ""),
            Q3ERR( SGX_QL_NETWORK_FAILURE, ""),
            Q3ERR( SGX_QL_SERVICE_TIMEOUT, ""),
            Q3ERR( SGX_QL_ERROR_BUSY, ""),

            Q3ERR( SGX_QL_UNKNOWN_MESSAGE_RESPONSE, "Unexpected error from the cache service"),
            Q3ERR( SGX_QL_PERSISTENT_STORAGE_ERROR, "Error storing the retrieved cached data in persistent memory"),
            Q3ERR( SGX_QL_ERROR_MESSAGE_PARSING_ERROR, "Message parsing error"),
            Q3ERR( SGX_QL_PLATFORM_UNKNOWN, "Platform was not found in the cache"),
        };
        return error_map;
    };

    const std::map<sgx_ql_qv_result_t, const char *>& getQuoteVerificationErrors() {
        /// (0x0000A000|(x))
        static const std::map<sgx_ql_qv_result_t, const char *> error_map {
            //SGX_QL_QV_RESULT_OK = 0x0000,                                            ///< The Quote verification passed and is at the latest TCB level
            Q3ERR( SGX_QL_QV_RESULT_CONFIG_NEEDED, "The Quote verification passed and the platform is patched to the latest TCB level but additional configuration of the SGX platform may be needed"),
            Q3ERR( SGX_QL_QV_RESULT_OUT_OF_DATE, "The Quote is good but TCB level of the platform is out of date. The platform needs patching to be at the latest TCB level"),
            Q3ERR( SGX_QL_QV_RESULT_OUT_OF_DATE_CONFIG_NEEDED, "The Quote is good but the TCB level of the platform is out of date and additional configuration of the SGX Platform at its current patching level may be needed. The platform needs patching to be at the latest TCB level"),
            Q3ERR( SGX_QL_QV_RESULT_INVALID_SIGNATURE, "The signature over the application report is invalid"),
            Q3ERR( SGX_QL_QV_RESULT_REVOKED, "The attestation key or platform has been revoked"),
            Q3ERR( SGX_QL_QV_RESULT_UNSPECIFIED, "The Quote verification failed due to an error in one of the input"),
            Q3ERR( SGX_QL_QV_RESULT_SW_HARDENING_NEEDED, "The TCB level of the platform is up to date, but SGX SW Hardening is needed"),
            Q3ERR( SGX_QL_QV_RESULT_CONFIG_AND_SW_HARDENING_NEEDED, "The TCB level of the platform is up to date, but additional configuration of the platform at its current patching level may be needed. Moreove, SGX SW Hardening is also needed"),
        };
        return error_map;
    };

}

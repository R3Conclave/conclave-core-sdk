#include <sgx_errors.h>
#include <map>

/* Error code returned by sgx_create_enclave */
namespace {
    const std::map<sgx_status_t, const char *>& getErrors();
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

namespace {
    const std::map<sgx_status_t, const char *>& getErrors() {
        static const std::map<sgx_status_t, const char *> error_map {
            { SGX_ERROR_UNEXPECTED, "SGX_ERROR_UNEXPECTED: Unexpected error"},
            { SGX_ERROR_INVALID_PARAMETER, "SGX_ERROR_INVALID_PARAMETER: The parameter is incorrect" },
            { SGX_ERROR_OUT_OF_MEMORY, "SGX_ERROR_OUT_OF_MEMORY: Not enough memory is available to complete this operation" },
            { SGX_ERROR_ENCLAVE_LOST, "SGX_ERROR_ENCLAVE_LOST: Enclave lost after power transition or used in child process created by linux:fork()" },
            { SGX_ERROR_INVALID_STATE, "SGX_ERROR_INVALID_STATE: SGX API is invoked in incorrect order or state" },
            { SGX_ERROR_INVALID_FUNCTION, "SGX_ERROR_INVALID_FUNCTION: The ecall/ocall index is invalid" },
            { SGX_ERROR_OUT_OF_TCS, "SGX_ERROR_OUT_OF_TCS: The enclave is out of TCS" },
            { SGX_ERROR_ENCLAVE_CRASHED, "SGX_ERROR_ENCLAVE_CRASHED: The enclave has crashed" },
            { SGX_ERROR_ECALL_NOT_ALLOWED, "SGX_ERROR_ECALL_NOT_ALLOWED: The ECALL is not allowed at this time, e.g. ecall is blocked by the dynamic entry table, or nested ecall is not allowed during initialization" },
            { SGX_ERROR_OCALL_NOT_ALLOWED, "SGX_ERROR_OCALL_NOT_ALLOWED: The OCALL is not allowed at this time, e.g. ocall is not allowed during exception handling" },
            { SGX_ERROR_STACK_OVERRUN, "SGX_ERROR_STACK_OVERRUN: The enclave is running out of stack" },
            { SGX_ERROR_UNDEFINED_SYMBOL, "SGX_ERROR_UNDEFINED_SYMBOL: The enclave image has undefined symbol." },
            { SGX_ERROR_INVALID_ENCLAVE, "SGX_ERROR_INVALID_ENCLAVE: The enclave image is not correct." },
            { SGX_ERROR_INVALID_ENCLAVE_ID, "SGX_ERROR_INVALID_ENCLAVE_ID: The enclave id is invalid" },
            { SGX_ERROR_INVALID_SIGNATURE, "SGX_ERROR_INVALID_SIGNATURE: The signature is invalid" },
            { SGX_ERROR_NDEBUG_ENCLAVE, "SGX_ERROR_NDEBUG_ENCLAVE: The enclave is signed as product enclave, and can not be created as debuggable enclave." },
            { SGX_ERROR_OUT_OF_EPC, "SGX_ERROR_OUT_OF_EPC: Not enough EPC is available to load the enclave" },
            { SGX_ERROR_NO_DEVICE, "SGX_ERROR_NO_DEVICE: Can't open SGX device. Ensure the SGX platform software is installed. If SGX has been software enabled a reboot may be required" },
            { SGX_ERROR_MEMORY_MAP_CONFLICT, "SGX_ERROR_MEMORY_MAP_CONFLICT: Page mapping failed in driver" },
            { SGX_ERROR_INVALID_METADATA, "SGX_ERROR_INVALID_METADATA: The metadata is incorrect." },
            { SGX_ERROR_DEVICE_BUSY, "SGX_ERROR_DEVICE_BUSY: Device is busy, mostly EINIT failed." },
            { SGX_ERROR_INVALID_VERSION, "SGX_ERROR_INVALID_VERSION: Metadata version is inconsistent between uRTS and sgx_sign or uRTS is incompatible with current platform." },
            { SGX_ERROR_MODE_INCOMPATIBLE, "SGX_ERROR_MODE_INCOMPATIBLE: The target enclave 32/64 bit mode or sim/hw mode is incompatible with the mode of current uRTS." },
            { SGX_ERROR_ENCLAVE_FILE_ACCESS, "SGX_ERROR_ENCLAVE_FILE_ACCESS: Can't open enclave file." },
            { SGX_ERROR_INVALID_MISC, "SGX_ERROR_INVALID_MISC: The MiscSelct/MiscMask settings are not correct" },
            { SGX_ERROR_INVALID_LAUNCH_TOKEN, "SGX_ERROR_INVALID_LAUNCH_TOKEN: The launch token is not correct" },
            { SGX_ERROR_MAC_MISMATCH, "SGX_ERROR_MAC_MISMATCH: Indicates verification error for reports, sealed datas, etc" },
            { SGX_ERROR_INVALID_ATTRIBUTE, "SGX_ERROR_INVALID_ATTRIBUTE: The enclave is not authorized" },
            { SGX_ERROR_INVALID_CPUSVN, "SGX_ERROR_INVALID_CPUSVN: The cpu svn is beyond platform's cpu svn value" },
            { SGX_ERROR_INVALID_ISVSVN, "SGX_ERROR_INVALID_ISVSVN: The isv svn is greater than the enclave's isv svn" },
            { SGX_ERROR_INVALID_KEYNAME, "SGX_ERROR_INVALID_KEYNAME: The key name is an unsupported value" },
            { SGX_ERROR_SERVICE_UNAVAILABLE, "SGX_ERROR_SERVICE_UNAVAILABLE: Indicates aesm didn't respond or the requested service is not supported" },
            { SGX_ERROR_SERVICE_TIMEOUT, "SGX_ERROR_SERVICE_TIMEOUT: The request to aesm timed out" },
            { SGX_ERROR_AE_INVALID_EPIDBLOB, "SGX_ERROR_AE_INVALID_EPIDBLOB: Indicates epid blob verification error" },
            { SGX_ERROR_SERVICE_INVALID_PRIVILEGE, "SGX_ERROR_SERVICE_INVALID_PRIVILEGE: Enclave has no privilege to get launch token" },
            { SGX_ERROR_EPID_MEMBER_REVOKED, "SGX_ERROR_EPID_MEMBER_REVOKED: The EPID group membership is revoked." },
            { SGX_ERROR_UPDATE_NEEDED, "SGX_ERROR_UPDATE_NEEDED: SGX needs to be updated" },
            { SGX_ERROR_NETWORK_FAILURE, "SGX_ERROR_NETWORK_FAILURE: Network connecting or proxy setting issue is encountered" },
            { SGX_ERROR_AE_SESSION_INVALID, "SGX_ERROR_AE_SESSION_INVALID: Session is invalid or ended by server" },
            { SGX_ERROR_BUSY, "SGX_ERROR_BUSY: The requested service is temporarily not availabe" },
            { SGX_ERROR_MC_NOT_FOUND, "SGX_ERROR_MC_NOT_FOUND: The Monotonic Counter doesn't exist or has been invalided" },
            { SGX_ERROR_MC_NO_ACCESS_RIGHT, "SGX_ERROR_MC_NO_ACCESS_RIGHT: Caller doesn't have the access right to specified VMC" },
            { SGX_ERROR_MC_USED_UP, "SGX_ERROR_MC_USED_UP: Monotonic counters are used out" },
            { SGX_ERROR_MC_OVER_QUOTA, "SGX_ERROR_MC_OVER_QUOTA: Monotonic counters exceeds quota limitation" },
            { SGX_ERROR_KDF_MISMATCH, "SGX_ERROR_KDF_MISMATCH: Key derivation function doesn't match during key exchange" },
            { SGX_ERROR_UNRECOGNIZED_PLATFORM, "SGX_ERROR_UNRECOGNIZED_PLATFORM: EPID Provisioning failed due to platform not recognized by backend serve" },
            { SGX_ERROR_NO_PRIVILEGE, "SGX_ERROR_NO_PRIVILEGE: Not enough privilege to perform the operation" },
            { SGX_ERROR_PCL_ENCRYPTED, "SGX_ERROR_PCL_ENCRYPTED: trying to encrypt an already encrypted enclave" },
            { SGX_ERROR_PCL_NOT_ENCRYPTED, "SGX_ERROR_PCL_NOT_ENCRYPTED: trying to load a plain enclave using sgx_create_encrypted_enclave" },
            { SGX_ERROR_PCL_MAC_MISMATCH, "SGX_ERROR_PCL_MAC_MISMATCH: section mac result does not match build time mac" },
            { SGX_ERROR_PCL_SHA_MISMATCH, "SGX_ERROR_PCL_SHA_MISMATCH: Unsealed key MAC does not match MAC of key hardcoded in enclave binary" },
            { SGX_ERROR_PCL_GUID_MISMATCH, "SGX_ERROR_PCL_GUID_MISMATCH: GUID in sealed blob does not match GUID hardcoded in enclave binary" },
            { SGX_ERROR_FILE_BAD_STATUS, "SGX_ERROR_FILE_BAD_STATUS: The file is in bad status, run sgx_clearerr to try and fix it" },
            { SGX_ERROR_FILE_NO_KEY_ID, "SGX_ERROR_FILE_NO_KEY_ID: The Key ID field is all zeros, can't re-generate the encryption key" },
            { SGX_ERROR_FILE_NAME_MISMATCH, "SGX_ERROR_FILE_NAME_MISMATCH: The current file name is different then the original file name (not allowed, substitution attack)" },
            { SGX_ERROR_FILE_NOT_SGX_FILE, "SGX_ERROR_FILE_NOT_SGX_FILE: The file is not an SGX file" },
            { SGX_ERROR_FILE_CANT_OPEN_RECOVERY_FILE, "SGX_ERROR_FILE_CANT_OPEN_RECOVERY_FILE: A recovery file can't be opened, so flush operation can't continue (only used when no EXXX is returned) " },
            { SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE, "SGX_ERROR_FILE_CANT_WRITE_RECOVERY_FILE: A recovery file can't be written, so flush operation can't continue (only used when no EXXX is returned) " },
            { SGX_ERROR_FILE_RECOVERY_NEEDED, "SGX_ERROR_FILE_RECOVERY_NEEDED: When openeing the file, recovery is needed, but the recovery process failed" },
            { SGX_ERROR_FILE_FLUSH_FAILED, "SGX_ERROR_FILE_FLUSH_FAILED: fflush operation (to disk) failed (only used when no EXXX is returned)" },
            { SGX_ERROR_FILE_CLOSE_FAILED, "SGX_ERROR_FILE_CLOSE_FAILED: fclose operation (to disk) failed (only used when no EXXX is returned)" },
        };
        return error_map;
    };
}

#include <sgx_capable.h>
#include <sgx_errors.h>
#include <sgx_device_status.h>
#include <enclave_platform.h>

bool checkAndEnableEnclaveSupport(bool enable, bool& wasEnabled, std::string& message) {
    // Check to see if SGX is supported on the platform
    sgx_device_status_t capStatus;
    sgx_status_t capReturnCode = sgx_cap_get_status(&capStatus);
    wasEnabled = false;

    // If SGX can be software enabled then attempt to enable it
    if (enable && (capReturnCode == SGX_SUCCESS) && (capStatus == SGX_DISABLED_SCI_AVAILABLE)) {
        // Attempt to software enable SGX on the platform. The function updates the status to reflect
        // the new platform support state
        capReturnCode = sgx_cap_enable_device(&capStatus);

        // Do we require root priviliges to enable SGX?
        if (capReturnCode == SGX_ERROR_NO_PRIVILEGE) {
            // Provide more detail to the user in this case
            message = "SGX_ERROR_NO_PRIVILEGE: Could not enable SGX. Run this once as root or administrator to enable SGX";
            return false;
        }

        // See if it is now enabled
        if ((capReturnCode == SGX_SUCCESS) && (capStatus == SGX_ENABLED)) {
            // Set the flag so the caller can retry their SGX operation
            wasEnabled = true;
        }
    }
    // Get any error as a string
    if (capReturnCode != SGX_SUCCESS) {
        message = getErrorMessage(capReturnCode);
        return false;
    }
    // If SGX is not enabled then get that as a string
    else if (capStatus != SGX_ENABLED) {
        message = getDeviceStatusMessage(capStatus);
        return false;
    }
    // SGX enabled if here
    return true;
}


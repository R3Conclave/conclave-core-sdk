#include <sgx_capable.h>
#include <sgx_errors.h>
#include <sgx_device_status.h>
#include <enclave_platform.h>

/**
 * Check enclave support on the current system.
 * Returns true if SGX is supported, false otherwise.
 * Returns a message when SGX is not available.
 */
bool checkEnclaveSupport(bool requireHardwareSupport, std::string& message) {
    // Check to see if SGX is supported on the platform
    sgx_device_status_t capStatus;
    sgx_status_t capReturnCode = sgx_cap_get_status(&capStatus);

    // Get any error as a string
    if (capReturnCode != SGX_SUCCESS) {
        message = getErrorMessage(capReturnCode);
        return false;
    }

    // If SGX is not enabled then get that as a string
    if (capStatus != SGX_ENABLED && requireHardwareSupport) {
        message = getDeviceStatusMessage(capStatus);
        return false;
    }

    // If we get here, all is well
    return true;
}

/**
 * Attempt to enable SGX on the current system.
 * Returns true if successfully activated, false otherwise.
 * Returns a message when failing to activate.
 */
bool enableHardwareEnclaveSupport(std::string& message) {
    // Check to see if SGX is supported on the platform
    sgx_device_status_t capStatus;
    sgx_status_t capReturnCode = sgx_cap_get_status(&capStatus);

    // Get any error as a string
    if (capReturnCode != SGX_SUCCESS) {
        message = getErrorMessage(capReturnCode);
        return false;
    }

    // If SGX is already enabled, do nothing
    if (capStatus == SGX_ENABLED) {
        return true;
    }

    if (capStatus != SGX_DISABLED_SCI_AVAILABLE) {
        message = getDeviceStatusMessage(capStatus);
        return false;
    }

    // Attempt to software enable SGX on the platform. The function updates the status to reflect
    // the new platform support state
    capReturnCode = sgx_cap_enable_device(&capStatus);

    // Do we require root privileges to enable SGX?
    if (capReturnCode == SGX_ERROR_NO_PRIVILEGE) {
        message = "SGX_ERROR_NO_PRIVILEGE: Could not enable SGX. Elevated privileges are required to enable SGX in software.";
        return false;
    } else if (capReturnCode != SGX_SUCCESS) {
        message = getErrorMessage(capReturnCode);
        return false;
    }

    // Check to ensure that that SGX is now enabled.
    if (capStatus != SGX_ENABLED) {
        message = getDeviceStatusMessage(capStatus);
        return false;
    }

    // If we get here, all is well
    return true;
}

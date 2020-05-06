#include <sgx_capable.h>
#include <map>

/* Status returned by sgx_cap_get_status */
namespace {
    const std::map<sgx_device_status_t, const char *>& getDeviceStatuses();
}

const char* getDeviceStatusMessage(sgx_device_status_t device_status) {
    const auto& sgxDeviceStatuses = getDeviceStatuses();
    auto iter = sgxDeviceStatuses.find(device_status);
    if (iter == sgxDeviceStatuses.end()) {
        return "Unknown device capability status code";
    } else {
        return iter->second;
    }
}

namespace {
    const std::map<sgx_device_status_t, const char *>& getDeviceStatuses() {
        static const std::map<sgx_device_status_t, const char *> device_status_map {
            { SGX_ENABLED, "SGX_ENABLED: SGX is enabled"},
            { SGX_DISABLED_REBOOT_REQUIRED, "SGX_DISABLED_REBOOT_REQUIRED: A reboot is required to finish enabling SGX" },
            { SGX_DISABLED_LEGACY_OS, "SGX_DISABLED_LEGACY_OS: SGX is disabled and cannot be enabled by software. Check your BIOS to see if it can be enabled manually" },
            { SGX_DISABLED, "SGX_DISABLED: SGX is not enabled on this platform. SGX might be disabled in the system BIOS or the system might not support SGX" },
            { SGX_DISABLED_SCI_AVAILABLE, "SGX_DISABLED_SCI_AVAILABLE: SGX is disabled but can be enabled by software" },
            { SGX_DISABLED_MANUAL_ENABLE, "SGX_DISABLED_MANUAL_ENABLE: SGX is disabled and the system BIOS does not support enabling SGX via software. Manually enable SGX in your BIOS" },
            { SGX_DISABLED_HYPERV_ENABLED, "SGX_DISABLED_HYPERV_ENABLED: Detected an unsupported version of Windows 10 with Hyper-V enabled" },
            { SGX_DISABLED_UNSUPPORTED_CPU, "SGX_DISABLED_UNSUPPORTED_CPU: SGX is not supported by the CPU in this system" }
        };
        return device_status_map;
    };
}
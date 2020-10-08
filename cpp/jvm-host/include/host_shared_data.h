#pragma once

#include "shared_data.h"
#include <sgx_urts.h>
#include <memory>
#include <map>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <thread>

namespace r3 { namespace conclave {

/**
 * Class that manages the lifecycle and updates to a block of shared memory that the host
 * maintains and passes to the enclave. This contains information that the enclave may find
 * useful, but as it comes from the host the enclave should not trust or rely on this information
 * for anything critical.
 */
class HostSharedData {
public:
    /**
     * Access the host to enclave shared interface instance
     */
    static HostSharedData& instance();

    /**
     * Get the shared object for a particular enclave.
     * If this is the first time this function has been called for an enclave then initialisation
     * is performed which may include starting a thread to keep shared data up-to-date.
     * Once this function has been called for an enclave, free() must be called when the enclave
     * is terminated.
     * 
     * @param enclave The enclave that we want to get shared data for.
     * @return Pointer to shared data.
     * 
     */
    SharedData* get(sgx_enclave_id_t enclave);

    /**
     * Free the shared data for a particular enclave.
     * 
     * @param enclave Enclave to free data for.
     * 
     */
    void free(sgx_enclave_id_t enclave);

private:
    explicit HostSharedData();
    virtual ~HostSharedData();

    void init();
    void deinit();

    void update();

    std::mutex mutex_;
    std::condition_variable wait_;
    std::map<sgx_enclave_id_t, std::unique_ptr<SharedData>> shared_data_;
    SharedData master_sd_;
    std::atomic_bool initialised_;
    std::thread thread_;
};

}}

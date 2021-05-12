#pragma once

#include "shared_data.h"
#include "conclave_tools.h"
#include "sgx_scoped_lock.h"

struct timespec;
struct timeval;

namespace r3 { namespace conclave {

/**
 * This class obtains the SharedData pointer from the host via an ocall then uses it to obtain
 * information without subsequent ocalls.
 * 
 * The information provided by this class should not be trusted by the enclave - it comes directly
 * from the host which could be compromised.
 */
class EnclaveSharedData {
public:
    /**
     * Access the host to enclave shared interface instance
     */
    static EnclaveSharedData& instance();

    /**
     * Get the real (current) time from the host via the shared object.
     * 
     * @return Current time in nanoseconds according to the (untrusted) host.
     *
     */
    uint64_t real_time();

    /**
     * Get the real (current) time from the host via the shared object as
     * a timespec structure.
     *
     * @param t The timespec to populate.
     *
     */
    void real_time(timespec& t);

    /**
     * Get the real (current) time from the host via the shared object as
     * a timeval structure.
     *
     * @param t The timeval to populate.
     *
     */
    void real_time(timeval& t);

    /**
     * Initialise the shared data if not done already.
     */
    void init();

private:
    explicit EnclaveSharedData();
    virtual ~EnclaveSharedData();

    void getSharedData(SharedData& sd) const;

    // The contents of them memory pointed to by shared_data_ can change at any time
    // out of the enclave's control so we need to decare the pointer volatile.
    // The "atomic" wrapper is to try to avoid locking the object when that's not necessary.
    std::atomic<volatile SharedData*> shared_data_;

    // Keep track of the last time returned by the enclave to ensure the clock
    // only runs forward.
    std::atomic<uint64_t> last_time_;

    mutable sgx_spinlock_t spinlock_;
};
}}

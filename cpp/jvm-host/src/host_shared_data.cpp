#include "host_shared_data.h"
#include <chrono>
#include <time.h>
#include <iostream>

namespace r3 { namespace conclave {

#define S_TO_NS(x) ((x) * 1000 * 1000 * 1000)
#define TIMESPEC_TO_NS(t) (uint64_t)(S_TO_NS(t.tv_sec) + t.tv_nsec)
#define MAX(x, y) ((x) > (y)) ? (x) : (y)

// The time resolution we provide via our thread loop is 1/10s
constexpr auto UPDATE_TIME_NS = 100 * 1000 * 1000;

/**
 * Class that manages the lifecycle and updates to a block of shared memory that the host
 * maintains and passes to the enclave. This contains information that the enclave may find
 * useful, but as it comes from the host the enclave should not trust or rely on this information
 * for anything critical.
 */

HostSharedData::HostSharedData() : initialised_(false) {
}

HostSharedData::~HostSharedData() {
    deinit();
}


HostSharedData& HostSharedData::instance() {
    static HostSharedData hsd;
    return hsd;
}

SharedData* HostSharedData::get(sgx_enclave_id_t enclave) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    // Find an existing enclave shared object.
    auto it = shared_data_.find(enclave);
    if (it != shared_data_.end()) {
        return it->second.get();
    }

    // First time accessing the data for this enclave. Make sure the shared data
    // has been initialised.
    init();

    shared_data_[enclave] = std::unique_ptr<SharedData>(new SharedData());
    return shared_data_[enclave].get();
}

void HostSharedData::free(sgx_enclave_id_t enclave) {
    bool need_deinit = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = shared_data_.find(enclave);
        if (it != shared_data_.end()) {
            shared_data_.erase(it);
        }
        // See if we've freed the last enclave.
        if (shared_data_.empty()) {
            need_deinit = true;
        }
    }
    if (need_deinit) {
        deinit();
    }

}

void HostSharedData::init() {
    if (!initialised_) {
        initialised_ = true;

        // Make sure the master shared object is initialised with data.
        update();

        // Create a thread for continuous updates.
        thread_ = std::thread([this]() {
            // Keep looping until the owning class is deinitialised.
            while (this->initialised_) {
                // Update the master shared object.
                std::unique_lock<std::mutex> lock(this->mutex_);
                update();

                // Update all enclave structures.
                for (auto it = this->shared_data_.begin(); it != this->shared_data_.end(); ++it) {
                    // We copy the data in because we don't want multiple enclaves to all share the same
                    // block of memory.
                    *it->second = master_sd_;
                }
                // Wait for a fixed timeout or for the parent to signal the thread should exit.
                this->wait_.wait_for(lock, std::chrono::nanoseconds(UPDATE_TIME_NS));
            }
        });
    }
}

void HostSharedData::deinit() {
    if (initialised_) {
        // Setting this to false causes the thread to exit once it comes out of its wait cycle.
        initialised_ = false;
        wait_.notify_all();
        thread_.join();
    }
}

void HostSharedData::update() {
    // Use the POSIX clock functions to get the time as the enclave emulates the same functions internally.
    uint64_t real_time = 0;
    struct timespec tm;
    if (clock_gettime(CLOCK_REALTIME, &tm) == 0) {
        real_time = TIMESPEC_TO_NS(tm);
    }
    // Update the master shared object.
    master_sd_.real_time = real_time;
}

}}

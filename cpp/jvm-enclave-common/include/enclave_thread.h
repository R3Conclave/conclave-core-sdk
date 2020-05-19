#pragma once

#include <memory>
#include <functional>
#include <os_support.h>
#include "aex_assert.h"
#include "sgx.h"

namespace r3 {
namespace conclave {

/**
 * A thread implementation for SGX enclaves
 */
class Thread {
public:

    /**
     * Return sgx status on start
     */
    sgx_status_t start();

    /**
     * Join this thread
     */
    void join();

    /**
     * An identifier of this thread
     */
    std::uint64_t id() const;

    Thread(Thread&&) = default;

    struct Impl;

private:
    friend class EnclaveThreadFactory;

    explicit Thread(std::function<void ()>);

    static std::shared_ptr<Impl> create(std::function<void()>);

    std::shared_ptr<Impl> impl_;

    Impl &pimpl() const;
};

/**
 * Centralized thread factory
 */
class EnclaveThreadFactory {
public:
    /**
     * Start a new thread running f(args...)
    */
    template<typename F, typename... Args>
    static Thread create(F &&f, Args &&... args) {
        aex_assert(is_alive());
        return Thread ([f, args...] { f(args...); });
    }

    /**
     * Shutdown thread factory waiting for all pending threads in execution.
     * Obvious note: calling shutdown() in a thread created by this factory will deadlock.
     */
    static void shutdown();

    /**
     * @return true iff the shutdown() has been called.
     */
    static bool is_alive();
};

} // namespace conclave {
} // namespace r3  {


#pragma once

#include <sgx.h>
#include <memory>
#include <mutex>
#include "graal_isolate.h"

namespace r3 { namespace conclave {

/**
 * Manage the enclave JVM
 */
class Jvm {
public:
    enum State {
        INITIALIZED,
        STARTED,     //< JVM created (automatically by the first attached thread)
        CLOSED       //< close() has been called, no new threads can attach.
    };

    /**
     * Attach the current thread to a Substrate VM context. The context remains valid until the thread exits the enclave,
     * at which point the context is destroyed. Re-entrant calls by the same thread are supported and return the same
     * thread context. The thread context is only destroyed when the thread completely exits the enclave.
     * The overhead of creating and destroying contexts was measured (with GraalVM 20.2) and shown to be negligible so
     * reuse of thread contexts and maintaining contexts over threads that have left the enclave is not necessary.
     * When the JVM is closed, GraalVM waits for all threads to exit and destroys the thread contexts.
     */
    std::shared_ptr<graal_isolatethread_t> attach_current_thread();

    /**
    * Current state.
    */
    State state();

    /**
     * Access the JVM instance.
     */
    static Jvm& instance();

    /**
     * Wait for all currently attached to the JVM then calls its destructor. Existing JNIEnv handlers owned by attached
     * thread will remain valid after this function is called.
     */
    void close();

    /**
     * Check to see if the thread is not CLOSED.
     */
    bool is_alive();

private:
    // Constructed via instance()
    explicit Jvm();
    struct JvmStateImpl {
        virtual std::shared_ptr<graal_isolatethread_t> attach_current_thread() = 0;
        virtual State state() const = 0;
        virtual void notify_detach(graal_isolatethread_t* p) {};
        virtual ~JvmStateImpl() {};
    };
    friend class JvmStateImpl_Started; //< Manage INITIALIZED and STARTED state
    friend class JvmStateImpl_Stopped; //< Manage CLOSED state
    friend class GraalThreadDeleter;
    std::mutex mutex_;
    std::unique_ptr<JvmStateImpl> impl_;
};

}} // namespace r3 { namespace conclave {
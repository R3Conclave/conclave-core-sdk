#pragma once

#include <sgx.h>
#include <jni.h>
#include <memory>
#include <mutex>

namespace r3 { namespace conclave {

/**
 * Manage the enclave JVM
 */
class Jvm {
public:
    enum State {
        INITIALIZED,
        STARTED,     //< JVM created (automatically by the first attached thread)
        CLOSED,      //< close() has been called, no new threads can attach.
        ZOMBIE       //< All attached threads have terminated, the JVM resources are being released.
    };

    /**
     * Attach current thread to JVM returning a managed pointer to the JNIEnv structure bound to the thread.
     * A thread keep its attached status until all the shared pointers to JNIEnv managed returned by this function are
     * alive. Repeated calls to this function from an already attached thread will always return pointers to the unique
     * JNIEnv structure bound to the calling thread, even when Jvm is CLOSED or ZOMBIE. If a non-attached thread
     * attempts to attach to a CLOSED  or ZOMBIE instance the result will be a null pointer.
     */
    std::shared_ptr<JNIEnv> attach_current_thread();

    /**
    * Current state
    */
    State state();

    /**
     * Access the JVM instance
     */
    static Jvm& instance();

    /**
     * Wait for all currently attached to the JVM then calls its destructor. Existing JNIEnv handlers owned by attached
     * thread will remain valid after this function is called.
     */
    void close();

    /**
     * Shortcut for checking if the current state is not CLOSED or ZOMBIE
     */
    bool is_alive();

    /**
     * Parameters affecting the heap max capacity estimate used by the singleton instance
     */
    static constexpr int c_estimated_nonjvm_memory = 58 * 1024 * 1024;
    static constexpr int c_estimated_stack_size = 512 * 1024;

private:
    // Constructed via instance()
    explicit Jvm(size_t jvm_heap_size, size_t jvm_stack_size);
    struct JvmStateImpl {
        virtual std::shared_ptr<JNIEnv> attach_current_thread() = 0;
        virtual State state() const = 0;
        virtual void notify_detach(JNIEnv*) = 0;
        virtual ~JvmStateImpl() {};
    };
    friend class JvmStateImpl_Started; //< Manage INITIALIZED and STARTED state
    friend class JvmStateImpl_Stopped; //< Manage CLOSED state
    friend class JNIEnvDisposer;
    std::mutex mutex_;
    std::condition_variable state_changed_;
    std::unique_ptr<JvmStateImpl> impl_;
};

}} // namespace r3 { namespace conclave {
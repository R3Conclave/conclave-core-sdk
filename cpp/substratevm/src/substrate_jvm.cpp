#include <aex_assert.h>
#include <substrate_jvm.h>
#include <os_support.h>
#include <mutex>
#include <set>

namespace r3 { namespace conclave {

/**************************************************************************/
class GraalThreadDeleter {
public:
    explicit GraalThreadDeleter(Jvm* owner) : owner_(owner) {}

    void operator()(graal_isolatethread_t* p) {
        if (p) {
            owner_->impl_->notify_detach(p);
        }
    }
private:
    Jvm* owner_;
};

/**************************************************************************/

class JvmStateImpl_Stopped: public Jvm::JvmStateImpl {
public:
    JvmStateImpl_Stopped(Jvm &owner) : owner_(owner) {
    }

    // We don't support attaching threads after the JVM has been stopped
    std::shared_ptr<graal_isolatethread_t> attach_current_thread() override  {
        return std::shared_ptr<graal_isolatethread_t> ();
    }

    Jvm::State state() const override {
        return Jvm::State::CLOSED;
    }

private:
    Jvm &owner_;
};

/**************************************************************************/

class JvmStateImpl_Started: public Jvm::JvmStateImpl {
public:

    explicit JvmStateImpl_Started (Jvm &owner) : owner_(owner) {
    }

    Jvm::State state() const override {
        return !isolate_ ? Jvm::State::INITIALIZED : Jvm::State::STARTED;
    }

    std::shared_ptr<graal_isolatethread_t> attach_current_thread() override  {
        if (state() ==  Jvm::State::INITIALIZED) {
            // This is the first thread entering, requires initializing the JVM instance
            return init_vm();
        } else {
            return attach_thread();
        }
    }

    // Start JVM shutdown process and handover to stopped state
    std::unique_ptr<Jvm::JvmStateImpl> destroy() {
        if (isolate_) {
            // We need a thread context to call this from
            graal_isolatethread_t* thread = nullptr;
            auto ret = graal_attach_thread(isolate_, &thread);
            aex_assert((ret == 0) && thread);
            // Note that when we destroy the isolate it is not necessary to drain the
            // threads_ set as graal_tear_down_isolate() detaches all threads.
            ret = graal_tear_down_isolate(thread);
            aex_assert(ret == 0);
        }
        return std::unique_ptr<Jvm::JvmStateImpl> (new JvmStateImpl_Stopped (owner_));
    }

    virtual void notify_detach(graal_isolatethread_t* p) {
        std::lock_guard<std::mutex> _ {owner_.mutex_};
        // The context pointer may be in the set more than once if the call is re-entrant.
        auto it = threads_.find(p);
        if (it != threads_.end()) {
            threads_.erase(it);
        }
        // If the context is no longer in the set then it can be destroyed.
        if (threads_.count(p) == 0) {
            graal_detach_thread(p);
        }
    }

private:
    // Initialize the JVM
    std::shared_ptr<graal_isolatethread_t> init_vm() {
        graal_isolatethread_t* thread = nullptr;
        auto ret = graal_create_isolate(nullptr, &isolate_, &thread);
        aex_assert(ret == 0);
        threads_.insert(thread);
        return std::shared_ptr<graal_isolatethread_t>(thread, GraalThreadDeleter(&owner_));
    }

    // Attach a new thread to the JVM
    std::shared_ptr<graal_isolatethread_t> attach_thread() {
        // The isolate must have been created
        aex_assert(isolate_);

        graal_isolatethread_t* thread = nullptr;
        auto ret = graal_attach_thread(isolate_, &thread);
        aex_assert(ret == 0);
        threads_.insert(thread);
        return std::shared_ptr<graal_isolatethread_t>(thread, GraalThreadDeleter(&owner_));
    }

    graal_isolate_t* isolate_ = nullptr;
    Jvm &owner_;

    // This set keeps track of each thread context returned by attach_current_thread(). It will
    // contain multiple of the same pointer if the enclave is reentrant on the same thread.
    // GraalThreadDeleter is used to remove entries from this set. When a unique pointer is
    // removed from the set then it is safe to delete the context.
    std::multiset<graal_isolatethread_t*> threads_;
};

/**************************************************************************/

Jvm::Jvm() : impl_ {new JvmStateImpl_Started(*this)} {
}

/**************************************************************************/

std::shared_ptr<graal_isolatethread_t> Jvm::attach_current_thread() {
    std::lock_guard<std::mutex> _ {mutex_};
    return impl_->attach_current_thread();
}

/**************************************************************************/

void Jvm::close() {
    std::unique_lock<std::mutex> state_lock{mutex_};
    auto *pimpl = dynamic_cast<JvmStateImpl_Started *>(impl_.get());
    if (pimpl) {
        auto closed_state = pimpl->destroy();
        impl_.swap(closed_state);
    }
    return;
}

/**************************************************************************/

bool Jvm::is_alive()  {
    const auto s = state();
    return s != State::CLOSED;
}

/**************************************************************************/

Jvm::State Jvm::state() {
    std::lock_guard<std::mutex> _ {mutex_};
    return impl_->state();
}

/**************************************************************************/

Jvm& Jvm::instance() {
    static Jvm result;
    return result;
}

}}

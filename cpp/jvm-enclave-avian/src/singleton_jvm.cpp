#include <sgx_thread.h>
#include <internal/global_data.h>
#include <aex_assert.h>
#include <singleton_jvm.h>
#include <os_support.h>
#include <unordered_set>
#include <unordered_map>
#include <vector>
#include <mutex>

namespace r3 { namespace sgx {

/**************************************************************************/

class JNIEnvDisposer {
public:
    explicit JNIEnvDisposer(Jvm* owner) : owner_(owner) {}
    void operator() (JNIEnv* p) {
        std::lock_guard<std::mutex> _ {owner_->mutex_};
        if (p != nullptr) {
            owner_->impl_->notify_detach(p);
        }
    };
private:
    Jvm* owner_;
};

/**************************************************************************/

class JvmStateImpl_Stopped: public Jvm::JvmStateImpl {
public:
    JvmStateImpl_Stopped(
            JavaVM *vm,
            std::unordered_map<JNIEnv*, std::weak_ptr<JNIEnv>> attached_threads,
            Jvm &owner)
    : vm_(vm)
    , attached_threads_(std::move(attached_threads))
    , owner_(owner) {
    }

    // If the current thread was already attached before the JVM shutdown started,
    // we return the JNIEnv pointer it already owns. Otherwise a nullptr is returned.
    std::shared_ptr<JNIEnv> attach_current_thread() override {
        if (vm_) {
            JNIEnv *result_raw = nullptr;
            vm_->GetEnv(reinterpret_cast<void **>(&result_raw), JNI_VERSION_1_2);
            if (result_raw != nullptr) {
                auto it = attached_threads_.find(result_raw);
                if (it != attached_threads_.end()) {
                    auto result = (it->second).lock();
                    return result;
                }
            }
        }
        return std::shared_ptr<JNIEnv> ();
    }

    Jvm::State state() const override {
        return attached_threads_.empty() ? Jvm::State::ZOMBIE : Jvm::State::CLOSED;
    }

    void notify_detach(JNIEnv *) override {
        if (vm_) {
            JNIEnv *current = nullptr;
            vm_->GetEnv(reinterpret_cast<void **>(&current), JNI_VERSION_1_2);
            vm_->DetachCurrentThread();
            attached_threads_.erase(current);
            if (attached_threads_.empty()) {
                owner_.state_changed_.notify_all();
            }
        }
    }

private:
    JavaVM *vm_ = nullptr;
    std::unordered_map<JNIEnv*, std::weak_ptr<JNIEnv>> attached_threads_;
    Jvm &owner_;
};

/**************************************************************************/

class JvmStateImpl_Started: public Jvm::JvmStateImpl {
public:

    explicit JvmStateImpl_Started (Jvm &owner,
                                   const size_t jvm_heap_size,
                                   const size_t jvm_stack_size)
    : owner_(owner) {
        snprintf(xmxOption_, sizeof(xmxOption_), "-Xmx%lu",
                 static_cast<size_t>(jvm_heap_size));
        snprintf(xssOption_, sizeof(xssOption_), "-Xss%lu",
                 static_cast<size_t>(jvm_stack_size));
        vm_options_ = std::vector<JavaVMOption> {
                // Tell Avian to call the functions above to find the embedded jar data.
                // We separate the app into boot and app jars because some code does not
                // expect to be loaded via the boot classloader.
                {const_cast<char *>("-Xbootclasspath:[embedded_file_boot_jar]"),  nullptr},
                {const_cast<char *>("-Djava.class.path=[embedded_file_app_jar]"), nullptr},
#ifdef SGX_SIM
                {const_cast<char *>("-Dsgx.mode=sim"),                            nullptr},
#elif defined(SGX)
                {const_cast<char *>("-Dsgx.mode=hw"),                             nullptr},
#endif
                {xmxOption_,                                                      nullptr},
                {xssOption_,                                                      nullptr}
        };
    }

    Jvm::State state() const override {
        return (vm_ == nullptr) ? Jvm::State::INITIALIZED : Jvm::State::STARTED;
    }

    std::shared_ptr<JNIEnv> attach_current_thread() override  {
        if (state() ==  Jvm::State::INITIALIZED) {
            // This is the first thread entering, requires initializing the JVM instance
            return init_vm();
        } else {
            JNIEnv* result_raw = nullptr;
            vm_->GetEnv(reinterpret_cast<void**>(&result_raw), JNI_VERSION_1_2);
            if (result_raw != nullptr) {
                // The current thread is already attached to the JVM
                auto it = attached_threads_.find(result_raw);
                aex_assert(it != attached_threads_.end());
                auto result = (it->second).lock();
                aex_assert(result);
                return result;
            } else {
                // Potentially reuse an already allocated jniEnv structure
                result_raw = reuse();
                vm_->AttachCurrentThread(reinterpret_cast<void **>(&result_raw), nullptr);
                return wrap_jnienv_ptr(result_raw);
            }
        }
    }

    virtual void notify_detach(JNIEnv *p) {
        // Recycle a free JNIEnv*
        if (p) {
            jnienv_pool_.insert(p);
            attached_threads_.erase(p);
        }
    }

    // Start JVM shutdown process and handover to stopped state
    std::unique_ptr<Jvm::JvmStateImpl> destroy() {
        // Instance not initialized, nothing to destroy
        if (vm_ != nullptr) {
            // Detach all JNIEnv in the pool
            for (JNIEnv *e: jnienv_pool_) {
                vm_->AttachCurrentThread(reinterpret_cast<void **>(&e), nullptr);
                vm_->DetachCurrentThread();
            }
        }

        return std::unique_ptr<Jvm::JvmStateImpl> (
                new JvmStateImpl_Stopped (
                        vm_,
                        std::move(attached_threads_),
                        owner_));
    }

    JavaVM *vm_ptr() const {
        return vm_;
    }

private:
    friend class JNIEnvDisposer;

    // Initialize Avian JVM
    std::shared_ptr<JNIEnv> init_vm() {
        JNIEnv* root_thread = nullptr;
        JavaVMInitArgs vmArgs = {
                .version = JNI_VERSION_1_2,
                .nOptions = static_cast<int>(vm_options_.size()),
                .options = vm_options_.data(),
                .ignoreUnrecognized = JNI_FALSE
        };
        auto ret = JNI_CreateJavaVM(&vm_, reinterpret_cast<void**>(&root_thread), &vmArgs);
        aex_assert(ret == JNI_OK);
        return wrap_jnienv_ptr(root_thread);
    }

    // Return a JNIEnv* from the pool or nullptr if none is available
    JNIEnv* reuse() {
        JNIEnv* result = nullptr;
        if (!jnienv_pool_.empty()) {
            const auto front_it = jnienv_pool_.begin();
            result = *front_it;
            jnienv_pool_.erase(front_it);
        }
        return result;
    }

    // A smart pointer wrapping JNIEnv* that reclaims back the pointer into the pool
    // when it goes out of scope
    std::shared_ptr<JNIEnv> wrap_jnienv_ptr(JNIEnv *p) {
        auto result = std::shared_ptr<JNIEnv>(p, JNIEnvDisposer(&owner_));
        attached_threads_[p] = std::weak_ptr<JNIEnv>(result);
        return result;
    }

    JavaVM *vm_ = nullptr;
    std::vector<JavaVMOption> vm_options_;
    Jvm &owner_;
    std::unordered_set<JNIEnv*> jnienv_pool_;
    std::unordered_map<JNIEnv*, std::weak_ptr<JNIEnv>> attached_threads_;
    char xmxOption_[32];
    char xssOption_[32];
};

/**************************************************************************/

Jvm::Jvm(const size_t jvm_heap_size, const size_t jvm_stack_size)
        : impl_ {new JvmStateImpl_Started (*this, jvm_heap_size, jvm_stack_size)} {
}

/**************************************************************************/

std::shared_ptr<JNIEnv> Jvm::attach_current_thread() {
    std::lock_guard<std::mutex> _ {mutex_};
    return impl_->attach_current_thread();
}

/**************************************************************************/

void Jvm::close() {
    std::unique_lock<std::mutex> state_lock{mutex_};
    JavaVM *vm = nullptr;
    auto *pimpl = dynamic_cast<JvmStateImpl_Started *>(impl_.get());
    if (pimpl) {
        vm = pimpl->vm_ptr();
        auto closed_state = pimpl->destroy();
        impl_.swap(closed_state);
    }
    if (vm) {
        // Note: there seems to be some race conditions affecting DestroyJavaVM
        // if some concurrent native threads are still attached to the JVM, therefore
        // we wait for such threads here before invoking it.
        state_changed_.wait(state_lock, [&] {
            return impl_->state() == State::ZOMBIE;
        });
        vm->DestroyJavaVM();
    }
    return;
}

/**************************************************************************/

bool Jvm::is_alive()  {
    const auto s = state();
    return s != State::CLOSED && s != State::ZOMBIE;
}

/**************************************************************************/

Jvm::State Jvm::state() {
    std::lock_guard<std::mutex> _ {mutex_};
    return impl_->state();
}

/**************************************************************************/

Jvm& Jvm::instance() {
    static Jvm result {
            g_global_data.heap_size - c_estimated_nonjvm_memory - c_estimated_stack_size * 20,
            c_estimated_stack_size
    };
    return result;
}

}}

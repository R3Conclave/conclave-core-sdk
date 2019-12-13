#include "enclave_thread.h"

#include <deque>
#include <memory>
#include <functional>
#include <mutex>
#include <future>
#include <aex_assert.h>
#include <jvm_t.h>
#include <os_support.h>

using Task = std::function<void()>;

namespace r3 {
namespace sgx {

/************************************************************************/

enum class ThreadState {
    CREATED = 0,
    STARTED = 1,
    COMPLETED = 2
};

class Thread::Impl {
public:
    explicit Impl(Task task)
            : state_(ThreadState::CREATED)
            , sgx_status_(SGX_SUCCESS)
            , task_(std::move(task)) {}

    void set_state(ThreadState state) {
        std::unique_lock<std::mutex> _ {mutex_};
        state_ = state;
        cond_var_.notify_all();
    }

    void set_state_on_start(sgx_status_t sgx_status) {
        std::unique_lock<std::mutex> _ {mutex_};
        state_ = (sgx_status == SGX_SUCCESS) ? ThreadState::STARTED : ThreadState::COMPLETED;
        sgx_status_ = sgx_status;
    }

    sgx_status_t start() {
        std::unique_lock<std::mutex> lock {mutex_};
        cond_var_.wait(lock, [this] {
            return state_ != ThreadState::CREATED; });
        return sgx_status_;
    }

    void join() {
        std::unique_lock<std::mutex> lock {mutex_};
        cond_var_.wait(lock, [this] {
            return state_ == ThreadState::COMPLETED; });
    }

    void run() {
        set_state(ThreadState::STARTED);
        task_();
        set_state(ThreadState::COMPLETED);
    }

private:
    std::mutex mutex_;
    std::condition_variable cond_var_;
    ThreadState state_;
    sgx_status_t sgx_status_;
    std::function<void()> task_;
};

namespace {
    class EnclaveThreadFactoryImpl;

    /************************************************************************/


    class RunnableCheckpoint {
    public:
        RunnableCheckpoint(EnclaveThreadFactoryImpl &owner);
        ~RunnableCheckpoint();
    private:
        EnclaveThreadFactoryImpl &owner_;
    };


    /************************************************************************/

    class EnclaveThreadFactoryImpl: public EnclaveThreadFactory {
    public:
        explicit EnclaveThreadFactoryImpl()
                : shutdown_started_(false), running_threads_(0) {}

        EnclaveThreadFactoryImpl(const EnclaveThreadFactoryImpl &) = delete;

        std::shared_ptr<Thread::Impl> create(Task f) {
            aex_assert(f);
            auto result = std::make_shared<Thread::Impl>((std::move(f)));
            {
                std::unique_lock<std::mutex> lock{mutex_};
                queue_.push_back(result);
            }
            sgx_status_t r = SGX_SUCCESS;
            const auto ret = ocall_request_thread(&r);
            result->set_state_on_start((ret == SGX_SUCCESS) ? r : ret);
            return result;
        }

        void attach_host_thread() {
            auto runnable = [&] {
                std::unique_lock<std::mutex> lock{mutex_};
                aex_assert(!queue_.empty());
                const auto next = queue_.front();
                queue_.pop_front();
                return next;
            }();
            ocall_complete_request_thread();
            RunnableCheckpoint _ {*this};
            runnable->run();
        }

        bool is_alive() {
            std::unique_lock<std::mutex> guard{mutex_};
            return !shutdown_started_;
        }

        void shutdown() {
            std::unique_lock<std::mutex> guard{mutex_};
            shutdown_started_ = true;
            shutdown_completed_.wait(guard, [&] { return (running_threads_ == 0); });
        }

        static EnclaveThreadFactoryImpl &instance() {
            static EnclaveThreadFactoryImpl c_result{};
            return c_result;
        }

    private:
        std::mutex mutex_;
        std::deque<std::shared_ptr<Thread::Impl>> queue_;
        std::condition_variable shutdown_completed_;
        bool shutdown_started_ = false;
        size_t running_threads_ = 0;
        friend class RunnableCheckpoint;
        void detach_thread() {
            std::lock_guard<std::mutex> _{mutex_};
            --running_threads_;
            if (running_threads_ == 0 && shutdown_started_) {
                shutdown_completed_.notify_all();
            }
        }
    };

    RunnableCheckpoint::RunnableCheckpoint(EnclaveThreadFactoryImpl &owner)
        : owner_(owner) {
            std::unique_lock<std::mutex> _{owner_.mutex_};
            ++owner.running_threads_;
         }

    RunnableCheckpoint::~RunnableCheckpoint() {
        owner_.detach_thread();
    }

} // namespace {

/************************************************************************/

Thread::Thread(Task run)
    : impl_(EnclaveThreadFactoryImpl::instance().create(std::move(run))) {}

std::uint64_t Thread::id() const {
    aex_assert(impl_);
    return reinterpret_cast<std::uint64_t>(impl_.get());
}

sgx_status_t Thread::start() {
    return pimpl().start();
}

void Thread::join() {
    pimpl().join();
}

Thread::Impl& Thread::pimpl() const {
    aex_assert(impl_);
    return *impl_;
}

/************************************************************************/

bool EnclaveThreadFactory::is_alive() {
    return EnclaveThreadFactoryImpl::instance().is_alive();
}

void EnclaveThreadFactory::shutdown() {
    return EnclaveThreadFactoryImpl::instance().shutdown();
}

} // namespace sgx {
} // namespace r3  {

void ecall_attach_thread() {
    r3::sgx::EnclaveThreadFactoryImpl::instance().attach_host_thread();
}

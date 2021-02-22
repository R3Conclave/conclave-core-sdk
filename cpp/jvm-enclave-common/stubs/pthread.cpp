//
// OS Stubs for functions declared in pthread.h
//
#include "vm_enclave_layer.h"
#include "enclave_shared_data.h"
#include <pthread.h>
#include <map>
#include <mutex>
#include "conclave-timespec.h"

// Internal to the Linux SGX SDK but we need information on the current thread.
#include <internal/thread_data.h>

// These two symbols are defined as parameters to the linker when running native-image.
// __ImageBase is a symbol that is at the address at the base of the image. __StackSize is
// a symbol at the fake address of &__ImageBase + size of the stack defined in the enclave
// configuration. We can subtract one address from the other to get the actual stack size.
extern unsigned long __StackSize;
extern unsigned long __ImageBase;
static unsigned long stack_size_pages = (unsigned long)((unsigned long long)&__StackSize - (unsigned long long)&__ImageBase);

namespace r3 { namespace conclave {

// Notes on pthread_attr_t:
// The SGX SDK provides a subset of pthread functions and types, including pthread_attr_t.
// The SGX SDK version of this type defines pthread_attr_t as a pointer to a pthread_attr.
// We need to associate and return more information than the SGX SDK provides so we use our 
// own internal structure type, redefining pthread_t to an integer that is a key to finding
// the relevant thread data.
//
// The only SDK function that takes this type as a parameter is pthread_create() which, looking
// at the source code, currently just marks the parameter as unused. However, we should guard
// against the wrong pointer being passed into these functions.

/**
 * This class provides a mapping between a pthread_attr_t pointer and a thread_data_t object.
 * This is achieved by storing a uint32_t value inside the memory pointed to by pthread_attr_t
 * which in our implementation is guaranteed to be large enough to hold a 32 bit value.
 * The lifecycle of the thread_data_t object must match that of the pthread_attr_t pointer.
 */
class PthreadData {
public:
    static PthreadData& instance() {
        static PthreadData pd;
        return pd;
    }

    /**
     * Creates a new mapping between a pthread_attr_t and a thread_data_t.
     * @param attr The thread attribute pointer to create mapping for.
     * @return The thread data object or nullptr if out of handles.
     */
    thread_data_t* create(const pthread_attr_t* attr) {
        thread_data_t* retval = nullptr;
        
        if (attr) {
            std::lock_guard<std::mutex> lock(mutex_);
            // Find an unused handle.
            int handle;
            for (handle = 0; handle < MAX_HANDLES; ++handle) {
                if (!thread_data_[handle]) {
                    retval = new thread_data_t;
                    memset(retval, 0, sizeof(thread_data_t));
                    thread_data_[handle].reset(retval);
                    // Store the handle in the thread attribute.
                    *(uint32_t*)attr = handle;
                    break;
                }
            }
        }
        return retval;
    }

    /**
     * Gets an existing mapping between a pthread_attr_t and a thread_data_t.
     * @param attr The thread attribute to get the mapping for.
     * @return The thread data associated with the attribute or nullptr if
     *         the mapping does not exist.
     */
    thread_data_t* get(const pthread_attr_t* attr) {
        thread_data_t* retval = nullptr;

        if (attr) {
            std::lock_guard<std::mutex> lock(mutex_);
            // The index into our array is stored as a 32 bit value in attr.
            uint32_t handle = *(uint32_t*)attr;
            if (handle < MAX_HANDLES) {
                retval = thread_data_[handle].get();
            }
        }
        return retval;
    }

    /**
     * Destroys the mapping between the pthread_attr_t and thread_data_t, releasing
     * the memory for the thread_data_t.
     * @param attr The thread attribute to free the mapping for.
     */
    void free(const pthread_attr_t* attr) {
        if (attr) {
            std::lock_guard<std::mutex> lock(mutex_);
            // The index into our array is stored as a 32 bit value in attr.
            uint32_t handle = *(uint32_t*)attr;
            if (handle < MAX_HANDLES) {
                thread_data_[handle] = nullptr;
            }
        }
    }


private:
    PthreadData() {}

    std::mutex mutex_;
    static constexpr int MAX_HANDLES = 256;
    std::unique_ptr<thread_data_t> thread_data_[MAX_HANDLES];
};

} }

using namespace r3::conclave;

extern "C" {

int pthread_attr_init(pthread_attr_t *attr) {
    enclave_trace("pthread_attr_init\n");

    if (!attr) {
        return EINVAL;
    }

    // Create a new mapping to a thread_data_t.
    if (!PthreadData::instance().create(attr)) {
        return ENOMEM;
    }
    return 0;
}

int pthread_attr_destroy(pthread_attr_t *attr) {
    if (!attr) {
        enclave_trace("pthread_attr_destroy(invalid))\n");
        return EINVAL;
    }
    PthreadData::instance().free(attr);
    enclave_trace("pthread_attr_destroy(success)\n");
    return 0;
}

int pthread_attr_getguardsize (const pthread_attr_t *attr, size_t *guardsize) {
    thread_data_t* td = PthreadData::instance().get(attr);
    if (!td) {
        enclave_trace("pthread_attr_getguardsize(invalid))\n");
        return EINVAL;
    }
    *guardsize = 0;
    enclave_trace("pthread_attr_getguardsize -> *guardsize=0x%lX\n", *guardsize);
    return 0;
}

int pthread_attr_getstack(pthread_attr_t *attr, void **stackaddr, size_t *stacksize) {
    thread_data_t* td = PthreadData::instance().get(attr);
    if (!td) {
        enclave_trace("pthread_attr_getstack(invalid))\n");
        return EINVAL;
    }
    *stacksize = (size_t)(td->stack_base_addr - td->stack_limit_addr);
    enclave_trace("pthread_attr_getstack -> *stacksize=0x%lX\n", *stacksize);
    return 0;
}

int pthread_getattr_np(pthread_t thread, pthread_attr_t *attr) {
    enclave_trace("pthread_getattr_np\n");

    // Only support this function on the current thread
    if (thread != pthread_self()) {
        enclave_trace("pthread_getattr_np called from other thread\n");
        return EINVAL;
    }

    // Create a new instance of an attributes object and set it to the current thread data.
    int result = pthread_attr_init(attr);
    if (result != 0) {
        return result;
    }
    thread_data_t* td = PthreadData::instance().get(attr);
    if (!td) {
        return EINVAL;
    }
    memcpy(td, get_thread_data(), sizeof(thread_data_t));
    return 0;
}

int pthread_attr_setdetachstate(pthread_attr_t *attr, int detachstate) {
    enclave_trace("pthread_attr_setdetachstate\n");
    return 0;
}

int pthread_attr_setstacksize(pthread_attr_t *attr, size_t stacksize) {
    if (stacksize > (stack_size_pages * 4096)) {
        jni_throw("The JDK attempted to set the stack size greater than configured in the Conclave enclave configuration. "
                  "Please increase the stack allocation in the Conclave configuration for your project.");
    }
    enclave_trace("pthread_attr_setstacksize\n");
    return 0;
}

int pthread_setname_np(pthread_t thread, const char *name) {
    enclave_trace("pthread_setname_np\n");
    return -1;
}

int pthread_cond_timedwait(pthread_cond_t *__restrict cond, pthread_mutex_t *__restrict mutex, const struct timespec *__restrict abstime) {
    if (abstime) {
        struct timespec reltime;
        // The time passed to this function is always an absolute time. This poses a problem as the SGX SDK
        // does not have access to absolute time. Therefore we need to convert the time here to a relative
        // time. If time has elapsed since the caller determined the absolute time then our relative time 
        // will be inaccurate but it is the best we can do.
        // Convert seconds and nanoseconds to a single value
        uint64_t timeout = abstime->tv_sec * NS_PER_SEC + abstime->tv_nsec;
        uint64_t now = r3::conclave::EnclaveSharedData::instance().real_time();

        if (timeout <= now) {
            return ETIMEDOUT;
        }
        timeout -= now;
        reltime.tv_sec = timeout / NS_PER_SEC;
        reltime.tv_nsec = timeout % NS_PER_SEC;
        enclave_trace("pthread_cond_timedwait(tv_sec = %ld, tv_nsec = %ld)\n", reltime.tv_sec, reltime.tv_nsec);
        return _pthread_cond_timedwait(cond, mutex, &reltime);
    }
    else {
        enclave_trace("pthread_cond_timedwait(abstime == NULL)\n");
        return pthread_cond_wait(cond, mutex);
    }
}


int pthread_condattr_init(pthread_condattr_t *attr) {
    enclave_trace("pthread_condattr_init\n");
    return 0;
}

int pthread_condattr_setclock(pthread_condattr_t *attr, clockid_t clock_id) {
    enclave_trace("pthread_condattr_setclock\n");
    return 0;
}

}

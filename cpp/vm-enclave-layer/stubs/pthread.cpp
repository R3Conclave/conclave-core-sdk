//
// OS Stubs for functions declared in pthread.h
//
#include "vm_enclave_layer.h"
#include <pthread.h>
#include <map>

// Internal to the Linux SGX SDK but we need information on the current thread.
#include <internal/thread_data.h>

// Notes on pthread_attr_t:
// The SGX SDK provides a subset of pthread functions and types, including pthread_attr_t.
// The SDK version of this type includes a single 'reserved' field of type char. We need to
// associate and return more information than this so we use our own internal structure type.
// The only SDK function that takes this type as a parameter is pthread_create() which, looking
// at the source code, currently just marks the parameter as unused. However, we should guard
// against the wrong pointer being passed into these functions.
// 
// In order to do this we use the reserved value (as an unsigned char) as a handle into a
// map of structures. Therefore we can support up to 256 attribute structures at one time,
// returning ENOMEM if we run out of handles.

// Copied from pthread_imp.h in the Linux SGX SDK
typedef struct _pthread_attr
{
    char    reserved;
} pthread_attr;

// Maps the 'reserved' field from pthread_attr_t defined in pthread_impl.h to our thread data
static std::map<unsigned char, thread_data_t>    conclave_attr;
unsigned char next_conclave_attr = 0;

static thread_data_t* conclave_thread_data(const pthread_attr_t* attr) {
    // Find the thread data using the 'reserved' field as a handle.
    auto it = conclave_attr.find((*attr)->reserved);
    if (it != conclave_attr.end()) {
        return &it->second;
    }
    return nullptr;
}

extern "C" {

int pthread_attr_init(pthread_attr_t *attr) {
    enclave_trace("pthread_attr_init\n");
    if (!attr) {
        return EINVAL;
    }

    // Find the next empty slot to use as a handle.
    int handle = -1;
    for (unsigned index = 0; index < 256; ++index) {
        if (conclave_attr.find((index + next_conclave_attr) % 256) == conclave_attr.end()) {
            // Empty
            handle = (int)((index + next_conclave_attr) % 256);
            break;
        }
    }
    // See if we ran out of handles.
    if (handle == -1) {
        return ENOMEM;
    }
    // Initialise with an empty structure for now.
    memset(&conclave_attr[(unsigned char)handle], 0, sizeof(thread_data_t));
    next_conclave_attr = (unsigned char)(((int)next_conclave_attr + 1) % 256);

    (*attr)->reserved = (char)handle;
    return 0;
}

int pthread_attr_destroy(pthread_attr_t *attr) {
    if (!attr) {
        enclave_trace("pthread_attr_destroy(invalid))\n");
        return EINVAL;
    }
    auto it = conclave_attr.find((*attr)->reserved);
    if (it == conclave_attr.end()) {
        return EINVAL;
    }
    conclave_attr.erase(it);
    enclave_trace("pthread_attr_destroy(success)\n");
    return 0;
}

int pthread_attr_getguardsize (const pthread_attr_t *attr, size_t *guardsize) {
    thread_data_t* td = conclave_thread_data(attr);
    if (!td) {
        enclave_trace("pthread_attr_getguardsize(invalid))\n");
        return EINVAL;
    }
    *guardsize = 0;
    enclave_trace("pthread_attr_getguardsize -> *guardsize=0x%lX\n", *guardsize);
    return 0;
}

int pthread_attr_getstack(pthread_attr_t *attr, void **stackaddr, size_t *stacksize) {
    thread_data_t* td = conclave_thread_data(attr);
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
    thread_data_t* td = conclave_thread_data(attr);
    if (!td) {
        return EINVAL;
    }
    memcpy(td, get_thread_data(), sizeof(thread_data_t));
    return 0;
}

int pthread_attr_setdetachstate(pthread_attr_t *attr, int detachstate) {
    enclave_trace("pthread_attr_setdetachstate\n");
    return -1;
}

int pthread_attr_setstacksize(pthread_attr_t *attr, size_t stacksize) {
    enclave_trace("pthread_attr_setstacksize\n");
    return -1;
}

int pthread_setname_np(pthread_t thread, const char *name) {
    enclave_trace("pthread_setname_np\n");
    return -1;
}

int pthread_cond_timedwait(pthread_cond_t *__restrict __cond, pthread_mutex_t *__restrict __mutex, const struct timespec *__restrict __abstime) {
    enclave_trace("pthread_cond_timedwait\n");
    return 0;
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

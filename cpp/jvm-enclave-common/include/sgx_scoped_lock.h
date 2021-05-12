
#pragma once
#if defined(UNIT_TEST)
// If inside unit tests, mock the SGX definitions otherwise the compilation will fail.
using sgx_spinlock_t = int32_t;
#define sgx_spin_lock(x) \
    do {                 \
    } while (0);
#define sgx_spin_unlock(x) sgx_spin_lock(x)

#else
#include "sgx_spinlock.h"
#endif

namespace r3 { namespace conclave {

class sgx_scoped_lock {
    public:
    sgx_scoped_lock(sgx_spinlock_t& spinlock) : lock_(spinlock) {
        sgx_spin_lock(&lock_);
    }

    ~sgx_scoped_lock() {
        sgx_spin_unlock(&lock_);
    }

    private:
        sgx_spinlock_t& lock_;
    };
}} // namespace r3::conclave
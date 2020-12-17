#include "unistd.h"
#include "substrate_jvm.h"
#include "graal_isolate.h"
#include "vm_enclave_layer.h"

#include <limits>

extern "C" {
    int Java_com_r3_conclave_enclave_internal_substratevm_Unistd_read(graal_isolatethread_t*, int fd, char* buf, int count);
    int Java_com_r3_conclave_enclave_internal_substratevm_Unistd_pread(graal_isolatethread_t*, int fd, char* buf, int count, off_t offset, int* error);
    int Java_com_r3_conclave_enclave_internal_substratevm_Unistd_close(graal_isolatethread_t*, int fildes, int* error);
    int Java_com_r3_conclave_enclave_internal_substratevm_Unistd_lseek64(graal_isolatethread_t*, int fd, off64_t offset, int whence, int* error);
    int Java_com_r3_conclave_enclave_internal_substratevm_Unistd_write(graal_isolatethread_t*, int fd, const void *buf, int count);
    int Java_com_r3_conclave_enclave_internal_substratevm_Unistd_pwrite(graal_isolatethread_t*, int fd, const void *buf, int count, off_t offset, int* error);
    off64_t lseek64_impl(int fd, off64_t offset, int whence);
}

ssize_t read_impl(int fd, void* buf, size_t count) {
    assert(count <= std::numeric_limits<int>::max());
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    return Java_com_r3_conclave_enclave_internal_substratevm_Unistd_read(jniEnv.get(), fd, reinterpret_cast<char*>(buf), (int) count);
}

ssize_t pread_impl(int fd, void* buf, size_t count, off_t offset) {
    assert(count <= std::numeric_limits<int>::max());
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    int error = 0;
    auto ret = Java_com_r3_conclave_enclave_internal_substratevm_Unistd_pread(jniEnv.get(), fd, reinterpret_cast<char*>(buf), (int) count, offset, &error);
    if (ret == -1) {
        errno = error;
    }
    return ret;
}

int close_impl(int fildes) {
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    int error = 0;
    auto ret = Java_com_r3_conclave_enclave_internal_substratevm_Unistd_close(jniEnv.get(), fildes, &error);
    if (ret == -1) {
        errno = error;
    }
    return ret;
}
off64_t lseek64_impl(int fd, off64_t offset, int whence) {

    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    int error = 0;
    auto ret = Java_com_r3_conclave_enclave_internal_substratevm_Unistd_lseek64(jniEnv.get(), fd, offset, whence, &error);
    if (ret == -1) {
        errno = error;
    }
    return ret;
}

ssize_t write_impl(int fd, const void *buf, size_t count) {
    assert(count <= std::numeric_limits<int>::max());
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    return Java_com_r3_conclave_enclave_internal_substratevm_Unistd_write(jniEnv.get(), fd, buf, (int) count);
}

ssize_t pwrite_impl(int fd, const void *buf, size_t count, off_t offset) {
    assert(count <= std::numeric_limits<int>::max());
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    int error = 0;
    auto ret = Java_com_r3_conclave_enclave_internal_substratevm_Unistd_pwrite(jniEnv.get(), fd, buf, (int) count, offset, &error);
    if (ret == -1) {
        errno = error;
    }
    return ret;
}

#include "fcntl.h"

#include <string.h>
#include "graal_isolate.h"
#include "substrate_jvm.h"

extern "C" {
    int Java_com_r3_conclave_enclave_internal_substratevm_Fcntl_open(graal_isolatethread_t*, char* __file, int oflag, int fd);
}

int open_impl(const char *__file, int oflag, int fd) {
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    return Java_com_r3_conclave_enclave_internal_substratevm_Fcntl_open(jniEnv.get(), const_cast<char*>(__file), oflag, fd);
}

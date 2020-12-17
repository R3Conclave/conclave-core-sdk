#include <stdexcept>
#include "enclave_shared_data.h"
#include "substrate_jvm.h"
#include "sys_stat.h"
#include "graal_isolate.h"
#include "vm_enclave_layer.h"

extern "C" {
    bool Java_com_r3_conclave_enclave_internal_substratevm_Fcntl_isOpen(graal_isolatethread_t*, int fildes);
}

int __fxstat64_impl(int ver, int fildes, struct stat64 * stat_buf) {
    using namespace r3::conclave;
    auto jniEnv = Jvm::instance().jniEnv();
    if (!Java_com_r3_conclave_enclave_internal_substratevm_Fcntl_isOpen(jniEnv.get(), fildes)) {
        return -1;
    }
    memset(stat_buf, 0, sizeof(struct stat64));
    // TODO CON-265 report the correct file type.
    // Currently it reports the files being all types (regular file, directory, char device, block device, etc).
    stat_buf->st_mode = S_IFMT;
    return 0;
}
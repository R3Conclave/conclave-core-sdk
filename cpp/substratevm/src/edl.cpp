#include "edl.h"

#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include <string>

#include <jni.h>

#include "jvm_t.h"

#include "sgx_trts.h"
#include "sgx_utils.h"

#include "vm_enclave_layer.h"
#include "substrate_jvm.h"
#include "enclave_shared_data.h"

using namespace std;

// Define variables that are used by the JNI function 
// Java_com_r3_conclave_enclave_internal_Native_readAppJarChunk(). This JNI
// is not used in SVM enclaves so just set the start and end to 0
extern "C" {
extern const uint8_t *_binary_app_jar_start = 0;
extern const uint8_t *_binary_app_jar_end = 0;
}

extern "C" {
int printf(const char *s, ...);
}

void jvm_ecall(void *bufferIn, int bufferInLen) {
    enclave_trace(">>> Enclave\n");

    using namespace r3::conclave;
    auto &jvm = Jvm::instance();
    auto jniEnv = jvm.attach_current_thread();
    if (!jniEnv) {
        if (!jvm.is_alive()) {
            throw std::runtime_error("Attempt to attach a new thread after enclave destruction has started");
        }
    }

    // Make sure this enclave has determined the host shared data address
    EnclaveSharedData::instance().init();

    Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPoint(jniEnv.get(), reinterpret_cast<char*>(bufferIn), bufferInLen);
}

void ecall_finalize_enclave() {
    enclave_trace("ecall_finalize_enclave\n");
    using namespace r3::conclave;
    Jvm::instance().close();
}

void throw_jvm_runtime_exception(const char *message) {
    std::string msg(message);
    using namespace r3::conclave;
    auto &jvm = Jvm::instance();
    auto jniEnv = jvm.attach_current_thread();
    Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_runtimeError(jniEnv.get(), reinterpret_cast<char*>(&msg[0]), msg.size());
}

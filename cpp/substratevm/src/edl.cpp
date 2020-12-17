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
#include "enclave_init.h"

using namespace std;

// These two symbols are defined as parameters to the linker when running native-image.
// __ImageBase is a symbol that is at the address at the base of the image. __DeadlockTimeout is
// a symbol at the fake address of &__ImageBase + the deadlock timeout value configured as
// part of the Gradle enclave configuration.
// We can subtract one address from the other to get the actual value.
extern unsigned long __ImageBase;
extern unsigned long __DeadlockTimeout;
static uint64_t deadlock_timeout = (uint64_t)((uint64_t)&__DeadlockTimeout - (uint64_t)&__ImageBase);

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
    auto jniEnv = Jvm::instance().jniEnv();

    // Make sure this enclave has determined the host shared data address
    EnclaveSharedData::instance().init();

    Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPoint(jniEnv.get(), reinterpret_cast<char*>(bufferIn), bufferInLen);
}

void ecall_initialise_enclave(void* initStruct, int initStructLen) {
    if (!initStruct ||(initStructLen != sizeof(r3::conclave::EnclaveInit))) {
        throw std::runtime_error("Invalid configuration structure passed to ecall_initialise_enclave()");
    }
    r3::conclave::EnclaveInit* ei = static_cast<r3::conclave::EnclaveInit*>(initStruct);
    ei->deadlock_timeout_seconds = deadlock_timeout;
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
    Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_internalError(jniEnv.get(), reinterpret_cast<char*>(&msg[0]), msg.size());
}

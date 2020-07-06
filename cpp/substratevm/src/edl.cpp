#include "edl.h"

#include <cstdlib>
#include <cstring>
#include <stdexcept>

#include <jni.h>

#include "jvm_t.h"

#include "sgx_trts.h"
#include "sgx_utils.h"

#include "vm_enclave_layer.h"

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

static graal_isolatethread_t *thread = nullptr;

void jvm_ecall(void *bufferIn, int bufferInLen) {
    enclave_trace(">>> Enclave\n");

    /**
      * The first ecall passes the enclave class name to be instantiated
      * and is synchronized on the host JVM's side.
      */
    auto exitCode = -1;
    if (!thread && (exitCode = graal_create_isolate(nullptr, nullptr, &thread)) != 0) {
        enclave_print("Error on isolate creation or attach: %d\n", exitCode);
        return;
    }
    Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPoint(thread, reinterpret_cast<char*>(bufferIn), bufferInLen);
}

void ecall_finalize_enclave() {
    enclave_trace("ecall_finalize_enclave\n");
    if (graal_tear_down_isolate(thread) != 0) {
        enclave_print("Failed to cleanly shutdown enclave.\n");
    }
}

void throw_jvm_runtime_exception(const char *message) {
    throw runtime_error(message);
}

#pragma once

#include <cstdint>
#include "sgx_report.h"
#include "graal_isolate.h"

#ifdef __cplusplus
extern "C" {
#endif
void Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPoint(graal_isolatethread_t*, char*, int);
void Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPointCon1025(graal_isolatethread_t*, short, char, char*, int);
void Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_internalError(graal_isolatethread_t*, char*, int);

void jvm_ecall(void* bufferIn, int bufferSize);
void ecall_attach_thread(void);
void ecall_finalize_enclave();
void throw_jvm_runtime_exception(const char *message);

#ifdef __cplusplus
}
#endif

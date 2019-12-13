#ifndef JVM_T_H__
#define JVM_T_H__

#include <stdint.h>
#include <wchar.h>
#include <stddef.h>
#include "sgx_edger8r.h" /* for sgx_ocall etc. */

#include "sgx_report.h"

#include <stdlib.h> /* for size_t */

#define SGX_CAST(type, item) ((type)(item))

#ifdef __cplusplus
extern "C" {
#endif

void jvm_ecall(void* bufferIn, int bufferInLen);
void ecall_finalize_enclave(void);
void ecall_attach_thread(void);

sgx_status_t SGX_CDECL jvm_ocall(void* bufferIn, int bufferInLen);
sgx_status_t SGX_CDECL debug_print(const char* string, int n);
sgx_status_t SGX_CDECL ocall_request_thread(sgx_status_t* retval);
sgx_status_t SGX_CDECL ocall_complete_request_thread(void);
sgx_status_t SGX_CDECL sgx_oc_cpuidex(int cpuinfo[4], int leaf, int subleaf);
sgx_status_t SGX_CDECL sgx_thread_wait_untrusted_event_ocall(int* retval, const void* self);
sgx_status_t SGX_CDECL sgx_thread_set_untrusted_event_ocall(int* retval, const void* waiter);
sgx_status_t SGX_CDECL sgx_thread_setwait_untrusted_events_ocall(int* retval, const void* waiter, const void* self);
sgx_status_t SGX_CDECL sgx_thread_set_multiple_untrusted_events_ocall(int* retval, const void** waiters, size_t total);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

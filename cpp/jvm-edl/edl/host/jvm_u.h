#ifndef JVM_U_H__
#define JVM_U_H__

#include <stdint.h>
#include <wchar.h>
#include <stddef.h>
#include <string.h>
#include "sgx_edger8r.h" /* for sgx_status_t etc. */

#include "sgx_report.h"

#include <stdlib.h> /* for size_t */

#define SGX_CAST(type, item) ((type)(item))

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JVM_OCALL_DEFINED__
#define JVM_OCALL_DEFINED__
void SGX_UBRIDGE(SGX_NOCONVENTION, jvm_ocall, (void* bufferIn, int bufferInLen));
#endif
#ifndef DEBUG_PRINT_DEFINED__
#define DEBUG_PRINT_DEFINED__
void SGX_UBRIDGE(SGX_NOCONVENTION, debug_print, (const char* string, int n));
#endif
#ifndef OCALL_REQUEST_THREAD_DEFINED__
#define OCALL_REQUEST_THREAD_DEFINED__
sgx_status_t SGX_UBRIDGE(SGX_NOCONVENTION, ocall_request_thread, (void));
#endif
#ifndef OCALL_COMPLETE_REQUEST_THREAD_DEFINED__
#define OCALL_COMPLETE_REQUEST_THREAD_DEFINED__
void SGX_UBRIDGE(SGX_NOCONVENTION, ocall_complete_request_thread, (void));
#endif
#ifndef SGX_OC_CPUIDEX_DEFINED__
#define SGX_OC_CPUIDEX_DEFINED__
void SGX_UBRIDGE(SGX_CDECL, sgx_oc_cpuidex, (int cpuinfo[4], int leaf, int subleaf));
#endif
#ifndef SGX_THREAD_WAIT_UNTRUSTED_EVENT_OCALL_DEFINED__
#define SGX_THREAD_WAIT_UNTRUSTED_EVENT_OCALL_DEFINED__
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_wait_untrusted_event_ocall, (const void* self));
#endif
#ifndef SGX_THREAD_SET_UNTRUSTED_EVENT_OCALL_DEFINED__
#define SGX_THREAD_SET_UNTRUSTED_EVENT_OCALL_DEFINED__
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_set_untrusted_event_ocall, (const void* waiter));
#endif
#ifndef SGX_THREAD_SETWAIT_UNTRUSTED_EVENTS_OCALL_DEFINED__
#define SGX_THREAD_SETWAIT_UNTRUSTED_EVENTS_OCALL_DEFINED__
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_setwait_untrusted_events_ocall, (const void* waiter, const void* self));
#endif
#ifndef SGX_THREAD_SET_MULTIPLE_UNTRUSTED_EVENTS_OCALL_DEFINED__
#define SGX_THREAD_SET_MULTIPLE_UNTRUSTED_EVENTS_OCALL_DEFINED__
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_set_multiple_untrusted_events_ocall, (const void** waiters, size_t total));
#endif

sgx_status_t jvm_ecall(sgx_enclave_id_t eid, void* bufferIn, int bufferInLen);
sgx_status_t ecall_finalize_enclave(sgx_enclave_id_t eid);
sgx_status_t ecall_attach_thread(sgx_enclave_id_t eid);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

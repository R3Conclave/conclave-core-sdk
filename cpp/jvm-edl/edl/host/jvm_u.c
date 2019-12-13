#include "jvm_u.h"
#include <errno.h>

typedef struct ms_jvm_ecall_t {
	void* ms_bufferIn;
	int ms_bufferInLen;
} ms_jvm_ecall_t;

typedef struct ms_jvm_ocall_t {
	void* ms_bufferIn;
	int ms_bufferInLen;
} ms_jvm_ocall_t;

typedef struct ms_debug_print_t {
	const char* ms_string;
	int ms_n;
} ms_debug_print_t;

typedef struct ms_ocall_request_thread_t {
	sgx_status_t ms_retval;
} ms_ocall_request_thread_t;

typedef struct ms_sgx_oc_cpuidex_t {
	int* ms_cpuinfo;
	int ms_leaf;
	int ms_subleaf;
} ms_sgx_oc_cpuidex_t;

typedef struct ms_sgx_thread_wait_untrusted_event_ocall_t {
	int ms_retval;
	const void* ms_self;
} ms_sgx_thread_wait_untrusted_event_ocall_t;

typedef struct ms_sgx_thread_set_untrusted_event_ocall_t {
	int ms_retval;
	const void* ms_waiter;
} ms_sgx_thread_set_untrusted_event_ocall_t;

typedef struct ms_sgx_thread_setwait_untrusted_events_ocall_t {
	int ms_retval;
	const void* ms_waiter;
	const void* ms_self;
} ms_sgx_thread_setwait_untrusted_events_ocall_t;

typedef struct ms_sgx_thread_set_multiple_untrusted_events_ocall_t {
	int ms_retval;
	const void** ms_waiters;
	size_t ms_total;
} ms_sgx_thread_set_multiple_untrusted_events_ocall_t;

static sgx_status_t SGX_CDECL jvm_jvm_ocall(void* pms)
{
	ms_jvm_ocall_t* ms = SGX_CAST(ms_jvm_ocall_t*, pms);
	jvm_ocall(ms->ms_bufferIn, ms->ms_bufferInLen);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_debug_print(void* pms)
{
	ms_debug_print_t* ms = SGX_CAST(ms_debug_print_t*, pms);
	debug_print(ms->ms_string, ms->ms_n);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_ocall_request_thread(void* pms)
{
	ms_ocall_request_thread_t* ms = SGX_CAST(ms_ocall_request_thread_t*, pms);
	ms->ms_retval = ocall_request_thread();

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_ocall_complete_request_thread(void* pms)
{
	if (pms != NULL) return SGX_ERROR_INVALID_PARAMETER;
	ocall_complete_request_thread();
	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_sgx_oc_cpuidex(void* pms)
{
	ms_sgx_oc_cpuidex_t* ms = SGX_CAST(ms_sgx_oc_cpuidex_t*, pms);
	sgx_oc_cpuidex(ms->ms_cpuinfo, ms->ms_leaf, ms->ms_subleaf);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_sgx_thread_wait_untrusted_event_ocall(void* pms)
{
	ms_sgx_thread_wait_untrusted_event_ocall_t* ms = SGX_CAST(ms_sgx_thread_wait_untrusted_event_ocall_t*, pms);
	ms->ms_retval = sgx_thread_wait_untrusted_event_ocall(ms->ms_self);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_sgx_thread_set_untrusted_event_ocall(void* pms)
{
	ms_sgx_thread_set_untrusted_event_ocall_t* ms = SGX_CAST(ms_sgx_thread_set_untrusted_event_ocall_t*, pms);
	ms->ms_retval = sgx_thread_set_untrusted_event_ocall(ms->ms_waiter);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_sgx_thread_setwait_untrusted_events_ocall(void* pms)
{
	ms_sgx_thread_setwait_untrusted_events_ocall_t* ms = SGX_CAST(ms_sgx_thread_setwait_untrusted_events_ocall_t*, pms);
	ms->ms_retval = sgx_thread_setwait_untrusted_events_ocall(ms->ms_waiter, ms->ms_self);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL jvm_sgx_thread_set_multiple_untrusted_events_ocall(void* pms)
{
	ms_sgx_thread_set_multiple_untrusted_events_ocall_t* ms = SGX_CAST(ms_sgx_thread_set_multiple_untrusted_events_ocall_t*, pms);
	ms->ms_retval = sgx_thread_set_multiple_untrusted_events_ocall(ms->ms_waiters, ms->ms_total);

	return SGX_SUCCESS;
}

static const struct {
	size_t nr_ocall;
	void * table[9];
} ocall_table_jvm = {
	9,
	{
		(void*)jvm_jvm_ocall,
		(void*)jvm_debug_print,
		(void*)jvm_ocall_request_thread,
		(void*)jvm_ocall_complete_request_thread,
		(void*)jvm_sgx_oc_cpuidex,
		(void*)jvm_sgx_thread_wait_untrusted_event_ocall,
		(void*)jvm_sgx_thread_set_untrusted_event_ocall,
		(void*)jvm_sgx_thread_setwait_untrusted_events_ocall,
		(void*)jvm_sgx_thread_set_multiple_untrusted_events_ocall,
	}
};
sgx_status_t jvm_ecall(sgx_enclave_id_t eid, void* bufferIn, int bufferInLen)
{
	sgx_status_t status;
	ms_jvm_ecall_t ms;
	ms.ms_bufferIn = bufferIn;
	ms.ms_bufferInLen = bufferInLen;
	status = sgx_ecall(eid, 0, &ocall_table_jvm, &ms);
	return status;
}

sgx_status_t ecall_finalize_enclave(sgx_enclave_id_t eid)
{
	sgx_status_t status;
	status = sgx_ecall(eid, 1, &ocall_table_jvm, NULL);
	return status;
}

sgx_status_t ecall_attach_thread(sgx_enclave_id_t eid)
{
	sgx_status_t status;
	status = sgx_ecall(eid, 2, &ocall_table_jvm, NULL);
	return status;
}


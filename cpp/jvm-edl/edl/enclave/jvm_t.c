#include "jvm_t.h"

#include "sgx_trts.h" /* for sgx_ocalloc, sgx_is_outside_enclave */
#include "sgx_lfence.h" /* for sgx_lfence */

#include <errno.h>
#include <mbusafecrt.h> /* for memcpy_s etc */
#include <stdlib.h> /* for malloc/free etc */

#define CHECK_REF_POINTER(ptr, siz) do {	\
	if (!(ptr) || ! sgx_is_outside_enclave((ptr), (siz)))	\
		return SGX_ERROR_INVALID_PARAMETER;\
} while (0)

#define CHECK_UNIQUE_POINTER(ptr, siz) do {	\
	if ((ptr) && ! sgx_is_outside_enclave((ptr), (siz)))	\
		return SGX_ERROR_INVALID_PARAMETER;\
} while (0)

#define CHECK_ENCLAVE_POINTER(ptr, siz) do {	\
	if ((ptr) && ! sgx_is_within_enclave((ptr), (siz)))	\
		return SGX_ERROR_INVALID_PARAMETER;\
} while (0)

#define ADD_ASSIGN_OVERFLOW(a, b) (	\
	((a) += (b)) < (b)	\
)


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

static sgx_status_t SGX_CDECL sgx_jvm_ecall(void* pms)
{
	CHECK_REF_POINTER(pms, sizeof(ms_jvm_ecall_t));
	//
	// fence after pointer checks
	//
	sgx_lfence();
	ms_jvm_ecall_t* ms = SGX_CAST(ms_jvm_ecall_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	void* _tmp_bufferIn = ms->ms_bufferIn;
	int _tmp_bufferInLen = ms->ms_bufferInLen;
	size_t _len_bufferIn = _tmp_bufferInLen;
	void* _in_bufferIn = NULL;

	CHECK_UNIQUE_POINTER(_tmp_bufferIn, _len_bufferIn);

	//
	// fence after pointer checks
	//
	sgx_lfence();

	if (_tmp_bufferIn != NULL && _len_bufferIn != 0) {
		_in_bufferIn = (void*)malloc(_len_bufferIn);
		if (_in_bufferIn == NULL) {
			status = SGX_ERROR_OUT_OF_MEMORY;
			goto err;
		}

		if (memcpy_s(_in_bufferIn, _len_bufferIn, _tmp_bufferIn, _len_bufferIn)) {
			status = SGX_ERROR_UNEXPECTED;
			goto err;
		}

	}

	jvm_ecall(_in_bufferIn, _tmp_bufferInLen);

err:
	if (_in_bufferIn) free(_in_bufferIn);
	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_finalize_enclave(void* pms)
{
	sgx_status_t status = SGX_SUCCESS;
	if (pms != NULL) return SGX_ERROR_INVALID_PARAMETER;
	ecall_finalize_enclave();
	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_attach_thread(void* pms)
{
	sgx_status_t status = SGX_SUCCESS;
	if (pms != NULL) return SGX_ERROR_INVALID_PARAMETER;
	ecall_attach_thread();
	return status;
}

SGX_EXTERNC const struct {
	size_t nr_ecall;
	struct {void* ecall_addr; uint8_t is_priv; uint8_t is_switchless;} ecall_table[3];
} g_ecall_table = {
	3,
	{
		{(void*)(uintptr_t)sgx_jvm_ecall, 0, 0},
		{(void*)(uintptr_t)sgx_ecall_finalize_enclave, 0, 0},
		{(void*)(uintptr_t)sgx_ecall_attach_thread, 0, 0},
	}
};

SGX_EXTERNC const struct {
	size_t nr_ocall;
	uint8_t entry_table[9][3];
} g_dyn_entry_table = {
	9,
	{
		{1, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
		{0, 0, 0, },
	}
};


sgx_status_t SGX_CDECL jvm_ocall(void* bufferIn, int bufferInLen)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_bufferIn = bufferInLen;

	ms_jvm_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_jvm_ocall_t);
	void *__tmp = NULL;


	CHECK_ENCLAVE_POINTER(bufferIn, _len_bufferIn);

	if (ADD_ASSIGN_OVERFLOW(ocalloc_size, (bufferIn != NULL) ? _len_bufferIn : 0))
		return SGX_ERROR_INVALID_PARAMETER;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_jvm_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_jvm_ocall_t));
	ocalloc_size -= sizeof(ms_jvm_ocall_t);

	if (bufferIn != NULL) {
		ms->ms_bufferIn = (void*)__tmp;
		if (memcpy_s(__tmp, ocalloc_size, bufferIn, _len_bufferIn)) {
			sgx_ocfree();
			return SGX_ERROR_UNEXPECTED;
		}
		__tmp = (void *)((size_t)__tmp + _len_bufferIn);
		ocalloc_size -= _len_bufferIn;
	} else {
		ms->ms_bufferIn = NULL;
	}
	
	ms->ms_bufferInLen = bufferInLen;
	status = sgx_ocall(0, ms);

	if (status == SGX_SUCCESS) {
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL debug_print(const char* string, int n)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_string = string ? strlen(string) + 1 : 0;

	ms_debug_print_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_debug_print_t);
	void *__tmp = NULL;


	CHECK_ENCLAVE_POINTER(string, _len_string);

	if (ADD_ASSIGN_OVERFLOW(ocalloc_size, (string != NULL) ? _len_string : 0))
		return SGX_ERROR_INVALID_PARAMETER;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_debug_print_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_debug_print_t));
	ocalloc_size -= sizeof(ms_debug_print_t);

	if (string != NULL) {
		ms->ms_string = (const char*)__tmp;
		if (_len_string % sizeof(*string) != 0) {
			sgx_ocfree();
			return SGX_ERROR_INVALID_PARAMETER;
		}
		if (memcpy_s(__tmp, ocalloc_size, string, _len_string)) {
			sgx_ocfree();
			return SGX_ERROR_UNEXPECTED;
		}
		__tmp = (void *)((size_t)__tmp + _len_string);
		ocalloc_size -= _len_string;
	} else {
		ms->ms_string = NULL;
	}
	
	ms->ms_n = n;
	status = sgx_ocall(1, ms);

	if (status == SGX_SUCCESS) {
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL ocall_request_thread(sgx_status_t* retval)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_ocall_request_thread_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_ocall_request_thread_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_ocall_request_thread_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_ocall_request_thread_t));
	ocalloc_size -= sizeof(ms_ocall_request_thread_t);

	status = sgx_ocall(2, ms);

	if (status == SGX_SUCCESS) {
		if (retval) *retval = ms->ms_retval;
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL ocall_complete_request_thread(void)
{
	sgx_status_t status = SGX_SUCCESS;
	status = sgx_ocall(3, NULL);

	return status;
}
sgx_status_t SGX_CDECL sgx_oc_cpuidex(int cpuinfo[4], int leaf, int subleaf)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_cpuinfo = 4 * sizeof(int);

	ms_sgx_oc_cpuidex_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_oc_cpuidex_t);
	void *__tmp = NULL;

	void *__tmp_cpuinfo = NULL;

	CHECK_ENCLAVE_POINTER(cpuinfo, _len_cpuinfo);

	if (ADD_ASSIGN_OVERFLOW(ocalloc_size, (cpuinfo != NULL) ? _len_cpuinfo : 0))
		return SGX_ERROR_INVALID_PARAMETER;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_oc_cpuidex_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_oc_cpuidex_t));
	ocalloc_size -= sizeof(ms_sgx_oc_cpuidex_t);

	if (cpuinfo != NULL) {
		ms->ms_cpuinfo = (int*)__tmp;
		__tmp_cpuinfo = __tmp;
		if (_len_cpuinfo % sizeof(*cpuinfo) != 0) {
			sgx_ocfree();
			return SGX_ERROR_INVALID_PARAMETER;
		}
		memset(__tmp_cpuinfo, 0, _len_cpuinfo);
		__tmp = (void *)((size_t)__tmp + _len_cpuinfo);
		ocalloc_size -= _len_cpuinfo;
	} else {
		ms->ms_cpuinfo = NULL;
	}
	
	ms->ms_leaf = leaf;
	ms->ms_subleaf = subleaf;
	status = sgx_ocall(4, ms);

	if (status == SGX_SUCCESS) {
		if (cpuinfo) {
			if (memcpy_s((void*)cpuinfo, _len_cpuinfo, __tmp_cpuinfo, _len_cpuinfo)) {
				sgx_ocfree();
				return SGX_ERROR_UNEXPECTED;
			}
		}
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_wait_untrusted_event_ocall(int* retval, const void* self)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_sgx_thread_wait_untrusted_event_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_wait_untrusted_event_ocall_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_wait_untrusted_event_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_wait_untrusted_event_ocall_t));
	ocalloc_size -= sizeof(ms_sgx_thread_wait_untrusted_event_ocall_t);

	ms->ms_self = self;
	status = sgx_ocall(5, ms);

	if (status == SGX_SUCCESS) {
		if (retval) *retval = ms->ms_retval;
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_set_untrusted_event_ocall(int* retval, const void* waiter)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_sgx_thread_set_untrusted_event_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_set_untrusted_event_ocall_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_set_untrusted_event_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_set_untrusted_event_ocall_t));
	ocalloc_size -= sizeof(ms_sgx_thread_set_untrusted_event_ocall_t);

	ms->ms_waiter = waiter;
	status = sgx_ocall(6, ms);

	if (status == SGX_SUCCESS) {
		if (retval) *retval = ms->ms_retval;
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_setwait_untrusted_events_ocall(int* retval, const void* waiter, const void* self)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_sgx_thread_setwait_untrusted_events_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_setwait_untrusted_events_ocall_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_setwait_untrusted_events_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_setwait_untrusted_events_ocall_t));
	ocalloc_size -= sizeof(ms_sgx_thread_setwait_untrusted_events_ocall_t);

	ms->ms_waiter = waiter;
	ms->ms_self = self;
	status = sgx_ocall(7, ms);

	if (status == SGX_SUCCESS) {
		if (retval) *retval = ms->ms_retval;
	}
	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_set_multiple_untrusted_events_ocall(int* retval, const void** waiters, size_t total)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_waiters = total * sizeof(void*);

	ms_sgx_thread_set_multiple_untrusted_events_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_set_multiple_untrusted_events_ocall_t);
	void *__tmp = NULL;


	CHECK_ENCLAVE_POINTER(waiters, _len_waiters);

	if (ADD_ASSIGN_OVERFLOW(ocalloc_size, (waiters != NULL) ? _len_waiters : 0))
		return SGX_ERROR_INVALID_PARAMETER;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_set_multiple_untrusted_events_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_set_multiple_untrusted_events_ocall_t));
	ocalloc_size -= sizeof(ms_sgx_thread_set_multiple_untrusted_events_ocall_t);

	if (waiters != NULL) {
		ms->ms_waiters = (const void**)__tmp;
		if (_len_waiters % sizeof(*waiters) != 0) {
			sgx_ocfree();
			return SGX_ERROR_INVALID_PARAMETER;
		}
		if (memcpy_s(__tmp, ocalloc_size, waiters, _len_waiters)) {
			sgx_ocfree();
			return SGX_ERROR_UNEXPECTED;
		}
		__tmp = (void *)((size_t)__tmp + _len_waiters);
		ocalloc_size -= _len_waiters;
	} else {
		ms->ms_waiters = NULL;
	}
	
	ms->ms_total = total;
	status = sgx_ocall(8, ms);

	if (status == SGX_SUCCESS) {
		if (retval) *retval = ms->ms_retval;
	}
	sgx_ocfree();
	return status;
}


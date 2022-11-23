#ifndef __DCAP_H_
#define __DCAP_H_

#include <dlfcn.h>
#include <time.h>
#include <sgx_report.h>
#include <sgx_pce.h>
#include <sgx_ql_lib_common.h>
#include <sgx_dcap_ql_wrapper.h>
#include <sgx_dcap_quoteverify.h>

#include <string>
#include <vector>

namespace r3::conclave::dcap {

#define SGX_QL_DECL(name,...) typedef quote3_error_t (*fun_##name##_t)(__VA_ARGS__); \
    fun_##name##_t name

    class QuotingAPI {

        void* comm_handle;
        void* urts_handle;
        void* qe3_handle;
        void* pce_handle;

        void* ql_handle;
        void* qp_handle;

        sgx_ql_qve_collateral_t* collateral;

        SGX_QL_DECL(sgx_ql_set_path,sgx_ql_path_type_t,const char *);
        SGX_QL_DECL(sgx_qe_set_enclave_load_policy,sgx_ql_request_policy_t);
        SGX_QL_DECL(sgx_qe_cleanup_by_policy);

        SGX_QL_DECL(sgx_qe_get_target_info, sgx_target_info_t*);
        SGX_QL_DECL(sgx_qe_get_quote_size,uint32_t*);
        SGX_QL_DECL(sgx_qe_get_quote,sgx_report_t *,uint32_t,uint8_t*);

        SGX_QL_DECL(sgx_ql_get_quote_verification_collateral, const uint8_t*, uint16_t, const char* pck_ca, sgx_ql_qve_collateral_t**);
        SGX_QL_DECL(sgx_ql_free_quote_verification_collateral, sgx_ql_qve_collateral_t *p_quote_collateral);

    public:
        typedef std::vector<std::string> Errors;

        bool init(const std::string& path, const bool loadQuotingLibraries, Errors& errors);

        bool get_target_info(sgx_target_info_t* target_info, quote3_error_t& eval_result);
        bool get_quote_size(uint32_t* p_size, quote3_error_t& eval_result);
        bool get_quote(sgx_report_t* report, uint32_t size, uint8_t* data, quote3_error_t& eval_result);

        sgx_ql_qve_collateral_t* get_quote_verification_collateral(const uint8_t* fmspc, int pck_ca_type, quote3_error_t& result);
        bool free_quote_verification_collateral(quote3_error_t& eval_result);

        ~QuotingAPI();
    };
}

#endif

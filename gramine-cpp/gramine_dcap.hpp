#pragma once

#include <dlfcn.h>
#include <time.h>
#include "sgx_report.h"
#include "sgx_pce.h"
#include "sgx_ql_lib_common.h"
#include "sgx_dcap_ql_wrapper.h"
#include "sgx_dcap_quoteverify.h"

#include <string>
#include <vector>

#define SGX_QL_DECL(name,...) typedef quote3_error_t (*fun_##name##_t)(__VA_ARGS__); \
    fun_##name##_t name


namespace conclave {

    class QuotingAPI {

        void* qp_handle;

        // void* qv_handle;
        sgx_ql_qve_collateral_t* collateral;

        SGX_QL_DECL(sgx_qe_get_target_info, sgx_target_info_t*);

        SGX_QL_DECL(sgx_ql_get_quote_verification_collateral, const uint8_t*, uint16_t, const char* pck_ca, sgx_ql_qve_collateral_t**);
        SGX_QL_DECL(sgx_ql_free_quote_verification_collateral, sgx_ql_qve_collateral_t *p_quote_collateral);

        SGX_QL_DECL(sgx_get_fmspc_ca_from_quote, const uint8_t* , uint32_t, unsigned char*, uint32_t, unsigned char*, uint32_t);

    public:
        typedef std::vector<std::string> Errors;

        bool init(const std::string& path, Errors& errors);

        bool get_target_info(sgx_target_info_t* target_info, quote3_error_t& eval_result);
        bool get_quote_size(uint32_t* p_size, quote3_error_t& eval_result);
        bool get_quote(sgx_report_t* report, uint32_t size, uint8_t* data, quote3_error_t& eval_result);

        sgx_ql_qve_collateral_t* get_quote_verification_collateral(const uint8_t* fmspc, const char* pck_ca_type, quote3_error_t& eval_result);
        bool free_quote_verification_collateral(quote3_error_t& eval_result);

        ~QuotingAPI();
    };

    class DCAP {
    private:
        QuotingAPI*     quoting_lib_;

    public:
        DCAP(const std::string& path);
        ~DCAP();

        QuotingAPI& quotingLibrary();

    };

    class DCAPException : public std::exception {
    public:
        std::string message;

        DCAPException(const std::string& message) {
            this->message = message;
        }
    };
}

#include <iostream>
#include <dcap.h>
#include <qve_header.h>

namespace r3::conclave::dcap {

// UBUNTU
#define LIB_PATH(s) ("/usr/lib/x86_64-linux-gnu/" s)

#define SGX_QL_RESOLVE(name) name=(fun_##name##_t)dlsym(handle,#name); \
    if (name == nullptr) { std::cerr << "unresolved: " #name "\n"; _is_ready = false; }

    dllib::dllib(const char* filename){
        handle = dlopen(filename,RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr) {
            std::cerr << "dlopen failed: " << filename << "\n";
        }
    }

    dllib::~dllib(){
        dlclose(handle);
    }

    QuotingAPI::QuotingAPI()
        :dllib(LIB_PATH("libsgx_dcap_ql.so.1")){

        _is_ready = handle != nullptr;

        if (!_is_ready)
            return;

        SGX_QL_RESOLVE(sgx_qe_set_enclave_load_policy);
        SGX_QL_RESOLVE(sgx_qe_cleanup_by_policy);
        SGX_QL_RESOLVE(sgx_ql_set_path);

        SGX_QL_RESOLVE(sgx_qe_get_target_info);
        SGX_QL_RESOLVE(sgx_qe_get_quote_size);
        SGX_QL_RESOLVE(sgx_qe_get_quote);

        sgx_qe_set_enclave_load_policy(sgx_ql_request_policy_t::SGX_QL_PERSISTENT);

        auto const qe3 = LIB_PATH("libsgx_qe3.signed.so");
        if (sgx_ql_set_path(SGX_QL_QE3_PATH,qe3) != SGX_QL_SUCCESS){
            std::cerr << "sgx_ql_set_path failed: " << qe3 << "\n";
            _is_ready = false;
        }

        auto const pce = LIB_PATH("libsgx_pce.signed.so");
        if (sgx_ql_set_path(SGX_QL_PCE_PATH,pce) != SGX_QL_SUCCESS){
            std::cerr << "sgx_ql_set_path failed: " << pce << "\n";
            _is_ready = false;
        }

        auto const qpl = LIB_PATH("libdcap_quoteprov.so.1");
        if (sgx_ql_set_path(SGX_QL_QPL_PATH,qpl) != SGX_QL_SUCCESS){
            std::cerr << "sgx_ql_set_path failed: " << qpl << "\n";
            _is_ready = false;
        }
    }

    QuotingAPI::~QuotingAPI(){
        sgx_qe_cleanup_by_policy();
    }

    bool QuotingAPI::get_target_info(sgx_target_info_t* target_info, quote3_error_t& eval_result) {
        eval_result = sgx_qe_get_target_info(target_info);
        return eval_result == SGX_QL_SUCCESS;
    }

    bool QuotingAPI::get_quote_size(uint32_t* p_size, quote3_error_t& eval_result){
        eval_result = sgx_qe_get_quote_size(p_size);
        return eval_result == SGX_QL_SUCCESS;
    }

    bool QuotingAPI::get_quote(sgx_report_t* report, uint32_t size, uint8_t* data, quote3_error_t& eval_result){
        eval_result = sgx_qe_get_quote(report,size,data);
        return eval_result == SGX_QL_SUCCESS;
    }

    QuoteProviderAPI::QuoteProviderAPI()
        :dllib(LIB_PATH("libdcap_quoteprov.so.1")){

        _is_ready = handle != nullptr;

        if (!_is_ready)
            return;

        SGX_QL_RESOLVE(sgx_ql_get_quote_verification_collateral);
    }

    QuoteProviderAPI::~QuoteProviderAPI(){
        // free collateral
    }

    sgx_ql_qve_collateral_t* QuoteProviderAPI::get_quote_verification_collateral(const uint8_t* fmspc, int pck_ca_type, quote3_error_t& eval_result){

        const char* pck_ca = pck_ca_type == 1 ? "platform" : "processor";

        auto result = sgx_ql_get_quote_verification_collateral(fmspc, 6, pck_ca, &collateral);
        eval_result = result;

        return SGX_QL_SUCCESS == result ? collateral : nullptr;
    }
}

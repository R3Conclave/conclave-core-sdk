#include <dcap.h>
#include <qve_header.h>

namespace r3::conclave::dcap {

#define SGX_QL_RESOLVE(handle, name) name=(fun_##name##_t)dlsym(handle,#name); \
    if (name == nullptr) errors.push_back(std::string("unresolved: " #name))

    void* try_dlopen(const std::string& path, const char *filename, QuotingAPI::Errors& errors)
    {
        auto const fullname = path + "/" + filename;

        void* handle = dlopen(fullname.c_str(),RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr)
            errors.push_back(std::string("unable to load: ") + fullname);

        return handle;
    }

    bool QuotingAPI::init(const std::string& path, QuotingAPI::Errors& errors) {

        comm_handle = try_dlopen( path, "libsgx_enclave_common.so.1", errors);
        urts_handle = try_dlopen( path, "libsgx_urts.so", errors);
        pce_handle = try_dlopen( path, "libsgx_pce_logic.so", errors);
        qe3_handle = try_dlopen( path, "libsgx_qe3_logic.so", errors);

        ql_handle = try_dlopen( path, "libsgx_dcap_ql.so.1", errors);
        if (ql_handle != nullptr) {
            SGX_QL_RESOLVE(ql_handle, sgx_qe_set_enclave_load_policy);
            SGX_QL_RESOLVE(ql_handle, sgx_qe_cleanup_by_policy);
            SGX_QL_RESOLVE(ql_handle, sgx_ql_set_path);

            SGX_QL_RESOLVE(ql_handle, sgx_qe_get_target_info);
            SGX_QL_RESOLVE(ql_handle, sgx_qe_get_quote_size);
            SGX_QL_RESOLVE(ql_handle, sgx_qe_get_quote);

            sgx_qe_set_enclave_load_policy(sgx_ql_request_policy_t::SGX_QL_PERSISTENT);

            auto const qe3 = path + "/" + "libsgx_qe3.signed.so";
            if (sgx_ql_set_path(SGX_QL_QE3_PATH, qe3.c_str()) != SGX_QL_SUCCESS)
                errors.push_back(std::string("sgx_ql_set_path failed: ") + qe3);

            auto const pce = path + "/" + "libsgx_pce.signed.so";
            if (sgx_ql_set_path(SGX_QL_PCE_PATH, pce.c_str()) != SGX_QL_SUCCESS)
                errors.push_back(std::string("sgx_ql_set_path failed: ") + pce);

            auto const qpl = path + "/" + "libdcap_quoteprov.so.1";
            if (sgx_ql_set_path(SGX_QL_QPL_PATH, qpl.c_str()) != SGX_QL_SUCCESS)
                errors.push_back(std::string("sgx_ql_set_path failed: ") + qpl);
        }

        qp_handle = try_dlopen( path, "libdcap_quoteprov.so.1", errors);
        if(qp_handle != nullptr) {
            SGX_QL_RESOLVE(qp_handle, sgx_ql_get_quote_verification_collateral);
        }

        return errors.size() == 0;
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

    sgx_ql_qve_collateral_t* QuotingAPI::get_quote_verification_collateral(const uint8_t* fmspc, int pck_ca_type, quote3_error_t& eval_result){

        const char* pck_ca = pck_ca_type == 1 ? "platform" : "processor";

        collateral = nullptr;

        auto result = sgx_ql_get_quote_verification_collateral(fmspc, 6, pck_ca, &collateral);
        eval_result = result;

        return SGX_QL_SUCCESS == result ? collateral : nullptr;
    }
}

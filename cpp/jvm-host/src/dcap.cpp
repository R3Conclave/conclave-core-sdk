#include <dcap.h>
#include <sgx_qve_header.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstring>

namespace r3::conclave::dcap {

#define SGX_QL_RESOLVE(handle, name) name=(fun_##name##_t)dlsym(handle,#name); \
    if (name == nullptr) errors.push_back(std::string("unresolved: " #name))

    void* try_dlopen(const std::string& fullpath, QuotingAPI::Errors& errors)
    {
        void* handle = dlopen(fullpath.c_str(),RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr)
            errors.push_back(std::string("unable to load: ") + fullpath);

        return handle;
    }

    void* try_dlopen(const std::string& path, const char *filename, QuotingAPI::Errors& errors)
    {
        auto const fullpath = path + "/" + filename;
        return try_dlopen(fullpath, errors);
    }

    bool does_file_exist(const std::string& fullpath)
    {
        struct stat stats;
        return stat(fullpath.c_str(), &stats) == 0;
    }

    // check if there is a plugin installed at fixed locations
    // if not, default to bundled one
    const std::string get_plugin_path(const std::string& bundle){
        const char* plugin_filename = "libdcap_quoteprov.so.1";
        const char* plugin_legacy_filename = "libdcap_quoteprov.so";
        auto const locations = {
            std::string("/usr/lib/x86_64-linux-gnu"),
            std::string("/usr/lib"),
            bundle
        };

        for(auto const& path : locations){
            auto const& fullpath = path + "/" + plugin_filename;
            if (does_file_exist(fullpath))
                return fullpath;

            auto const& legacy_path = path + "/" + plugin_legacy_filename;
            if (does_file_exist(legacy_path))
                return legacy_path;
        }

        throw new std::exception(); // fatal, not suppose to happen
    }

    bool QuotingAPI::init(const std::string& path, const bool loadQuotingLibraries, Errors& errors) {
        //  The users can use SGX_AESM_ADDR environment variable to select
        //    whether some functions of the quoting library run "in-process" or "out-of-process.
        //  Conclave uses the "in-process" approach, and the SGX_AESM_ADDR variable
        //    needs to be unset. Note that the variable is unset only for the environment
        //    of the current process, not in the overall application or other processes.
        unsetenv("SGX_AESM_ADDR");

        auto const qpl = get_plugin_path(path);

        if (loadQuotingLibraries) {
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

                auto const qe3 = path + "/libsgx_qe3.signed.so";
                if (sgx_ql_set_path(SGX_QL_QE3_PATH, qe3.c_str()) != SGX_QL_SUCCESS)
                    errors.push_back(std::string("sgx_ql_set_path failed: ") + qe3);

                auto const pce = path + "/libsgx_pce.signed.so";
                if (sgx_ql_set_path(SGX_QL_PCE_PATH, pce.c_str()) != SGX_QL_SUCCESS)
                    errors.push_back(std::string("sgx_ql_set_path failed: ") + pce);

                auto const ide = path + "/libsgx_id_enclave.signed.so";
                if (sgx_ql_set_path(SGX_QL_IDE_PATH, ide.c_str()) != SGX_QL_SUCCESS)
                    errors.push_back(std::string("sgx_ql_set_path failed: ") + ide);

                if (sgx_ql_set_path(SGX_QL_QPL_PATH, qpl.c_str()) != SGX_QL_SUCCESS)
                    errors.push_back(std::string("sgx_ql_set_path failed: ") + qpl);
            }
        }
        qp_handle = try_dlopen( qpl, errors);

        if (qp_handle != nullptr) {
            SGX_QL_RESOLVE(qp_handle, sgx_ql_get_quote_verification_collateral);
            SGX_QL_RESOLVE(qp_handle, sgx_ql_free_quote_verification_collateral);
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

    bool QuotingAPI::free_quote_verification_collateral(quote3_error_t& eval_result) {
        if (collateral != nullptr) {
            eval_result = sgx_ql_free_quote_verification_collateral(collateral);
            collateral = nullptr;
            return eval_result == SGX_QL_SUCCESS;
        } else {
            //  Already freed or free not required, we return true in such case.
            return true;
        }
    }

    //  The users should not free sgx_ql_qve_collateral_t manually but they are expected to call free_quote_verification_collateral
    sgx_ql_qve_collateral_t* QuotingAPI::get_quote_verification_collateral(const uint8_t* fmspc, int pck_ca_type, quote3_error_t& eval_result){

        const char* pck_ca = pck_ca_type == 1 ? "platform" : "processor";

        collateral = nullptr;

        auto result = sgx_ql_get_quote_verification_collateral(fmspc, 6, pck_ca, &collateral);
        eval_result = result;

        return SGX_QL_SUCCESS == result ? collateral : nullptr;
    }

    QuotingAPI::~QuotingAPI() {
        if (collateral != nullptr) {
            //  Attempt to free the collateral in case it has not been freed (this should never be the case)
            //    We do not log potential errors here, we only want to close this gracefully.
            sgx_ql_free_quote_verification_collateral(collateral);
        }
    }
}

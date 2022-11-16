#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <iostream>

#include "gramine_dcap.hpp"

namespace conclave {

#define SGX_QL_RESOLVE(handle, name) name=(fun_##name##_t)dlsym(handle,#name); \
    if (name == nullptr) errors.push_back(std::string("unresolved: " #name))


    void printHex(const void* buf) {
        size_t count = 6;
        const uint8_t* c = (uint8_t*)buf;
        for (size_t i = 0; i < count; ++i) {
            printf("%02X, ", c[i]);
            if (((i + 1) % 16) == 0) {
                printf("\n");
            }
        }
        printf("\n");
    }

    void* try_dlopen(const std::string& fullpath, QuotingAPI::Errors& errors) {
        void* handle = dlopen(fullpath.c_str(),RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr) {
            std::cerr << "Failed to load " << fullpath << ": " << dlerror() << std::endl;
            errors.push_back(std::string("unable to load: ") + fullpath);
        }

        return handle;
    }

    void* try_dlopen(const std::string& path, const char *filename, QuotingAPI::Errors& errors) {
        auto const fullpath = path + "/" + filename;
        return try_dlopen(fullpath, errors);
    }

    bool does_file_exist(const std::string& fullpath) {
        struct stat stats;
        return stat(fullpath.c_str(), &stats) == 0;
    }

    // check if there is a plugin installed at fixed locations
    // if not, default to bundled one
    const std::string get_plugin_path(const std::string& bundle) {
        const char* plugin_filename = "libdcap_quoteprov.so.1";
        const char* plugin_legacy_filename = "libdcap_quoteprov.so";
        auto const locations = {
                                bundle,
                                std::string("/usr/lib/x86_64-linux-gnu"),
                                std::string("/usr/lib")
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

    bool QuotingAPI::init(const std::string& path, QuotingAPI::Errors& errors) {
        auto const qpl = get_plugin_path(path);

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

    bool QuotingAPI::get_quote(sgx_report_t* report, uint32_t size, uint8_t* data, quote3_error_t& eval_result) {
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
    sgx_ql_qve_collateral_t* QuotingAPI::get_quote_verification_collateral(const uint8_t* fmspc, const char* pck_ca, quote3_error_t& eval_result) {
        collateral = nullptr;
        printHex(fmspc);
        printf("Before sgx_ql_get_quote_verification_collateral: %s\n", pck_ca);

        auto result = sgx_ql_get_quote_verification_collateral(fmspc, 6, pck_ca, &collateral);
        eval_result = result;

        printf("After sgx_ql_get_quote_verification_collateral: result %d, pointer %p\n", result, collateral);
        return SGX_QL_SUCCESS == result ? collateral : nullptr;
    }

    QuotingAPI::~QuotingAPI() {

        if (collateral != nullptr) {
            //  Attempt to free the collateral in case it has not been freed (this should never be the case)
            //    We do not log potential errors here, we only want to close this gracefully.
            sgx_ql_free_quote_verification_collateral(collateral);
        }
    }

    DCAP::DCAP(const std::string& path) {
        QuotingAPI::Errors errors;
        try {
            quoting_lib_ = new QuotingAPI();
            if (!quoting_lib_->init(path, errors)) {
                std::string message("failed to initialize DCAP: ");
                for(auto &err : errors)
                    message += err + ";";

                std::cerr << "Errors: " << message << std::endl;

                delete quoting_lib_;
                quoting_lib_ = nullptr;
                throw DCAPException(message);
            }
        }
        catch(...){
            if (quoting_lib_ != nullptr) {
                delete quoting_lib_;
                quoting_lib_ = nullptr;
            }
            throw DCAPException("Failed to initialise DCAP library.");
        }
    }

    DCAP::~DCAP() {
        delete quoting_lib_;
    }

    QuotingAPI& DCAP::quotingLibrary() {
        return *quoting_lib_;
    }
}

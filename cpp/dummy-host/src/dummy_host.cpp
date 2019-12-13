#include <jvm_u.h>
#include <sgx_errors.h>
#include <sgx_urts.h>
#include <cstdlib>
#include <cstdio>
#include <enclave_metadata.h>
#include <ecall_context.h>

inline bool check_sgx_return_value(sgx_status_t ret)
{
    if (ret == SGX_SUCCESS)
    {
        return true;
    }
    else
    {
        printf("%s\n", getErrorMessage(ret));
        return false;
    }
}

void debug_print(const char *string) {
    printf("%s", string);
}

int main(int argc, char **argv)
{
    printf("SGX_DEBUG_FLAG = %d\n", SGX_DEBUG_FLAG);

    if (argc != 2)
    {
        puts("Usage: <binary> <signed.enclave.so>");
        return 1;
    }

    const char *enclave_path = argv[1];
    sgx_launch_token_t token = {0};
    sgx_enclave_id_t enclave_id = {0};
    int updated = 0;
    sgx_measurement_t mr_enclave;
    enclave_metadata_result_t hash_result = retrieve_enclave_metadata(enclave_path, mr_enclave.m);
    if (EHR_SUCCESS != hash_result) {
        printf("Unable to retrieve MRENCLAVE from enclave\n");
        exit(1);
    }
    if (!check_sgx_return_value(sgx_create_enclave(enclave_path, SGX_DEBUG_FLAG, &token, &updated, &enclave_id, nullptr))) {
        return 1;
    }
    EcallContext id(enclave_id, nullptr);

    const char *inputBlob = "<inputBlob passed from host>";

    char outputBlob[1024];

    int outputLen;
    if (!check_sgx_return_value(jvm_ecall(enclave_id, (void *) inputBlob, static_cast<int>(strlen(inputBlob)), &outputLen, outputBlob, 1024, -1))) {
        return 1;
    }

    puts("Enclave ran successfully!");

    return 0;
}

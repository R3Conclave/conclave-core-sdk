#pragma once

#include <sgx.h>

#include <sgx_ql_lib_common.h>
#include <qve_header.h>

const char* getErrorMessage(sgx_status_t status);

const char* getQuotingErrorMessage(uint32_t status);
const char* getQuoteGenerationErrorMessage(quote3_error_t status);
const char* getQuoteVerificationErrorMessage(sgx_ql_qv_result_t status);

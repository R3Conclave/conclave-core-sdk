#pragma once

#include <cstdint>
#include <internal/metadata.h>

extern "C" sgx_status_t retrieve_enclave_metadata(const char *path, metadata_t *metadata);

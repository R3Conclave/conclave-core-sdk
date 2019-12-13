package com.r3.sgx.enclavelethost.ias.schemas

enum class ManifestStatus {
    OK,
    UNKNOWN,
    INVALID,
    OUT_OF_DATE,
    REVOKED,
    RL_VERSION_MISMATCH
}
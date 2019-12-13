package com.r3.sgx.multiplex.common

enum class LoadEnclaveDiscriminator(val value: Byte) {
    CREATE(0),
    USE(1)
}
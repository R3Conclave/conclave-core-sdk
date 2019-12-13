package com.r3.sgx.multiplex.common

import com.r3.sgx.core.common.MuxId

enum class MultiplexDiscriminator(val id: MuxId) {
    LOAD(-1),
    UNLOAD(-2)
}

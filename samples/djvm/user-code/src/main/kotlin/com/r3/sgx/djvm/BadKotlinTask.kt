package com.r3.sgx.djvm

import java.util.function.Function

class BadKotlinTask : Function<String, Long> {
    override fun apply(input: String): Long {
        return javaClass.getField(input).getLong(this)
    }
}
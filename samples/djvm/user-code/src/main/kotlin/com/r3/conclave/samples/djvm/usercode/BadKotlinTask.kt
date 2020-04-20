package com.r3.conclave.samples.djvm.usercode

import java.util.function.Function

class BadKotlinTask : Function<String, Long> {
    override fun apply(input: String): Long {
        return javaClass.getField(input).getLong(this)
    }
}
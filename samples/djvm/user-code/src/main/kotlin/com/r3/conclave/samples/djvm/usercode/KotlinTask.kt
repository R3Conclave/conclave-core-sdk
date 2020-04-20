package com.r3.conclave.samples.djvm.usercode

import java.util.function.Function

class KotlinTask : Function<String, String> {
    override fun apply(input: String): String {
        return "Sandbox says: '$input'"
    }
}

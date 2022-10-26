package com.r3.conclave.plugin.enclave.gradle

import java.lang.IllegalArgumentException

enum class RuntimeType {
    Gramine,
    GraalVM;

    companion object {
        fun fromString(s: String): RuntimeType {
            return when (s.lowercase()) {
                "gramine" -> Gramine
                "graalvm" -> GraalVM
                else -> throw IllegalArgumentException("'$s' is not a valid runtime type.")
            }
        }
    }
}

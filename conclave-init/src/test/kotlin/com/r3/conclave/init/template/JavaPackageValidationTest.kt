package com.r3.conclave.init.template

import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JavaPackageValidationTest {
    companion object {
        @JvmStatic
        fun allowedPackageNames(): List<String> = listOf(
            "com",
            "com.megacorp",
            "com.test.myenclave",
            "_com._123",
        )

        @JvmStatic
        fun invalidPackageNames(): List<String> = listOf(
            "com.megacorp.HelloWorldEnclave",
            "com.Test",
            "Com.Test",
            "com.MyEnclave",
            "com.test.MyEnclave",
            "comTest.MyEnclave",
            "",
            "MyEnclave",
            "Com",
            "1com",
            "com.hy-phen",
            "123",
            "com.",
            ".com",
            "test.123com",
            "test.123_"
        )
    }

    @ParameterizedTest
    @MethodSource("allowedPackageNames")
    fun `allowed class names`(name: String) {
        assertDoesNotThrow { JavaPackage(name) }
    }

    @ParameterizedTest
    @MethodSource("invalidPackageNames")
    fun `invalid class names`(name: String) {
        assertThrows<IllegalArgumentException> { JavaPackage(name) }
    }
}
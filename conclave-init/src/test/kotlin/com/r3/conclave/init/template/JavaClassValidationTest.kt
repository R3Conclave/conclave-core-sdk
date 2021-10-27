package com.r3.conclave.init.template

import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JavaClassValidationTest {
    companion object {
        @JvmStatic
        fun allowedClassNames(): List<String> = listOf(
            "MegaEnclave",
            "megaEnclave",
            "megaenclave",
            "i3",
            "αρετη",
            "MAX_VALUE",
            "isLetterOrDigit",
        )

        @JvmStatic
        fun invalidClassNames(): List<String> = listOf(
            "1MegaEnclave",
            "Mega@Enclave",
            "mega-enclave"
        )
    }

    @ParameterizedTest
    @MethodSource("allowedClassNames")
    fun `allowed class names`(name: String) {
        assertDoesNotThrow { JavaClass(name) }
    }

    @ParameterizedTest
    @MethodSource("invalidClassNames")
    fun `invalid class names`(name: String) {
        assertThrows<IllegalArgumentException> { JavaClass(name) }
    }
}

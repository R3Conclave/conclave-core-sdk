package com.r3.conclave.init

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.Path

class LanguageTest {
    @Test
    fun `kotlin file matches kotlin`() {
        assertTrue(
            Language.KOTLIN.matches(Path("/home/project/enclave/src/main/kotlin/com/foo/enclave/MyEnclave.kt"))
        )
    }

    @Test
    fun `kotlin file doesn't match java`() {
        assertFalse(
            Language.JAVA.matches(Path("/home/project/enclave/src/main/kotlin/com/foo/enclave/MyEnclave.kt"))
        )
    }

    @Test
    fun `java file matches java`() {
        assertTrue(
            Language.JAVA.matches(Path("/home/project/enclave/src/main/java/com/foo/enclave/MyEnclave.java"))
        )
    }

    @Test
    fun `java file doesn't match kotlin`() {
        assertFalse(
            Language.KOTLIN.matches(Path("/home/project/enclave/src/main/java/com/foo/enclave/MyEnclave.java"))
        )
    }

    @ParameterizedTest
    @EnumSource
    fun `build gradle matches either`(language: Language) {
        assertTrue(
            language.matches(Path("/home/project/enclave/build.gradle"))
        )
    }
}
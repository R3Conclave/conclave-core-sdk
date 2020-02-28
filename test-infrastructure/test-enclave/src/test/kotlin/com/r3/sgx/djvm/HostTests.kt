package com.r3.sgx.djvm

import com.r3.sgx.test.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Paths

/**
 * Run tests via JUnit on the host JVM, without invoking the enclave infrastructure.
 * Useful to verify the test are behaving as expected before invoking them in the enclave.
 */
class HostTests {

    companion object {
        @JvmStatic
        private val deterministicRTJarPath = Paths.get(System.getProperty("deterministic-rt.path"))
                ?: throw AssertionError("System property 'deterministic-rt.path' not set.")

        val mathsJarPath = Paths.get(System.getProperty("maths-tests-jar.path"))
                ?: throw AssertionError("System property 'maths-tests-jar.path' not set.")
        
        @JvmStatic
        val testCodeJarPath = Paths.get(System.getProperty("djvm-unit-tests-jar.path"))
                ?: throw AssertionError("System property 'test-code-jar.path' not set.")

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            val userSourcesJars = arrayOf(mathsJarPath.toUri().toURL())
            val bootstrapJars = arrayOf(deterministicRTJarPath.toUri().toURL())
            setup(bootstrapJars, userSourcesJars)
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun afterAll() {
            destroy()
        }
    }

    @ArgumentsSource(EnclaveTests.ClassArgumentProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun runTests(clazz: Class< in EnclaveJvmTest>) {
        runTest(clazz)
    }

    @ArgumentsSource(EnclaveTests.ClassArgumentProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun runSandboxedTests(clazz: Class< in EnclaveJvmTest>) {
        runSandboxedTest(clazz)
    }
}
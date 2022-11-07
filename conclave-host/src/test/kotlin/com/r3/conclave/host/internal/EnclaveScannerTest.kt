package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.EnclaveScanner.ScanResult
import com.r3.conclave.utilities.internal.copyResource
import io.github.classgraph.ClassGraph
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import org.junitpioneer.jupiter.cartesian.CartesianTest.Values
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class EnclaveScannerTest {
    @field:TempDir
    lateinit var classpathDir: Path
    private lateinit var enclaveScanner: EnclaveScanner

    @BeforeEach
    fun createScanner() {
        enclaveScanner = object : EnclaveScanner() {
            override fun createClassGraph(): ClassGraph = ClassGraph().overrideClasspath(classpathDir.toString())
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `single mock enclave`(namedSearch: Boolean) {
        val testClassFilePath = "${MockEnclave::class.java.name.replace('.', '/')}.class"
        javaClass.copyResource("/$testClassFilePath", classpathDir.resolve(testClassFilePath))
        val result = if (namedSearch) {
            enclaveScanner.findEnclave(MockEnclave::class.java.name)
        } else {
            enclaveScanner.findEnclave()
        }
        assertThat(result).isEqualTo(ScanResult.Mock(MockEnclave::class.java.name))
    }

    @CartesianTest
    fun `single non-mock enclave`(
        @Values(strings = ["release", "debug", "simulation"]) mode: String,
        @Values(strings = ["graalvm.so", "gramine.zip"]) type: String,
        @Values(booleans = [false, true]) namedSearch: Boolean
    ) {
        val file = classpathDir.resolve("com/r3/conclave/enclave/user-bundles/com.foo.bar.Enclave/$mode-$type")
        file.parent.createDirectories()
        file.createFile()
        val result = if (namedSearch) {
            enclaveScanner.findEnclave("com.foo.bar.Enclave")
        } else {
            enclaveScanner.findEnclave()
        }
        if (type == "graalvm.so") {
            assertThat(result).isInstanceOf(ScanResult.GraalVM::class.java)
        } else {
            assertThat(result).isInstanceOf(ScanResult.GramineDirect::class.java)
        }
        assertThat(result.enclaveClassName).isEqualTo("com.foo.bar.Enclave")
        assertThat(result.enclaveMode).isEqualTo(EnclaveMode.valueOf(mode.uppercase()))
    }

    class MockEnclave : Enclave()
}

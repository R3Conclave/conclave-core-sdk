package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.div

class PythonEnclaveAdapterJarTest : AbstractTaskTest() {
    override val taskName: String get() = "pythonEnclaveAdapterJar"
    override val output: Path get() = buildDir / "libs" / "$projectName-fat-all.jar"

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(output.toFile()).use { jar ->
            assertThat(jar.getJarEntry("com/r3/conclave/python/PythonEnclaveAdapter.class")).isNotNull
            val enclavePropertiesEntry = jar.getJarEntry("com/r3/conclave/python/enclave.properties")
            assertThat(enclavePropertiesEntry).isNotNull
            val enclaveProperties = jar.getInputStream(enclavePropertiesEntry).use {
                Properties().apply { load(it) }
            }
            assertThat(enclaveProperties).containsEntry("productID", "11")
            assertThat(enclaveProperties).containsEntry("revocationLevel", "12")
        }
    }
}

package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.div

class ShadowJarTest : AbstractTaskTest() {
    override val taskName: String get() = "shadowJar"
    override val output: Path get() = buildDir / "libs" / "$projectName-fat-all.jar"

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(output.toFile()).use { jar ->
            assertThat(jar.getJarEntry("com/test/enclave/TestEnclave.class")).isNotNull
            val enclaveProperties = jar.getInputStream(jar.getJarEntry("com/test/enclave/enclave.properties")).use {
                Properties().apply { load(it) }
            }
            assertThat(enclaveProperties).containsEntry("productID", "11")
            assertThat(enclaveProperties).containsEntry("revocationLevel", "12")
        }
    }
}

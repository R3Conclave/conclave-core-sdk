package com.r3.conclave.init.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class PathTransformersTest {
    @Test
    fun `transform package absolute`() {
        val transformer = TransformPackage(JavaPackage("com.megacorp"))
        val templatePath =
            Path("/home/template/enclave/src/test/java/com/r3/conclave/template/enclave/TemplateEnclaveTest.java")
        val expected = Path("/home/template/enclave/src/test/java/com/megacorp/enclave/TemplateEnclaveTest.java")

        assertEquals(expected, transformer.invoke(templatePath))
    }

    @Test
    fun `transform package relative`() {
        val transformer = TransformPackage(JavaPackage("com.megacorp"))
        val templatePath = Path("enclave/src/test/kotlin/com/r3/conclave/template/enclave/TemplateEnclaveTest.kt")
        val expected = Path("enclave/src/test/kotlin/com/megacorp/enclave/TemplateEnclaveTest.kt")

        assertEquals(expected, transformer.invoke(templatePath))
    }

    @Test
    fun `transform java class name`() {
        val transformer = TransformClassName(JavaClass("TemplateEnclave"), JavaClass("MegaEnclave"))
        val templatePath = Path("enclave/src/test/java/com/r3/conclave/template/enclave/TemplateEnclaveTest.kt")
        val expected = Path("enclave/src/test/java/com/r3/conclave/template/enclave/MegaEnclaveTest.kt")

        assertEquals(expected, transformer.invoke(templatePath))
    }

    @Test
    fun `transform kotlin class name`() {
        val transformer = TransformClassName(JavaClass("TemplateEnclave"), JavaClass("MegaEnclave"))
        val templatePath = Path("enclave/src/test/kotlin/com/r3/conclave/template/enclave/TemplateEnclaveTest.kt")
        val expected = Path("enclave/src/test/kotlin/com/r3/conclave/template/enclave/MegaEnclaveTest.kt")

        assertEquals(expected, transformer.invoke(templatePath))
    }

    @Test
    fun `transform base path`() {
        val transformer = TransformBasePath(Path("/home/template"), Path("/home/my/new/project"))
        val templatePath = Path("/home/template/signing/sample_private_key.pem")
        val expected = Path("/home/my/new/project/signing/sample_private_key.pem")

        assertEquals(expected, transformer.invoke(templatePath))
    }

    @Test
    fun `full transform`() {
        val templateFiles = sequenceOf(
            "/home/template/README.md",
            "/home/template/enclave/build.gradle",
            "/home/template/enclave/src/test/java/com/r3/conclave/template/enclave/TemplateEnclaveTest.java",
            "/home/template/enclave/src/main/java/com/r3/conclave/template/enclave/TemplateEnclave.java",
            "/home/template/build.gradle",
            "/home/template/gradlew.bat",
            "/home/template/settings.gradle",
            "/home/template/gradlew",
            "/home/template/gradle/wrapper/gradle-wrapper.properties",
            "/home/template/gradle/wrapper/gradle-wrapper.jar",
            "/home/template/signing/sample_private_key.pem",
            "/home/template/host/build.gradle"
        ).map(::Path)

        val expected = listOf(
            "/home/my/new/project/README.md",
            "/home/my/new/project/enclave/build.gradle",
            "/home/my/new/project/enclave/src/test/java/com/megacorp/enclave/MegaEnclaveTest.java",
            "/home/my/new/project/enclave/src/main/java/com/megacorp/enclave/MegaEnclave.java",
            "/home/my/new/project/build.gradle",
            "/home/my/new/project/gradlew.bat",
            "/home/my/new/project/settings.gradle",
            "/home/my/new/project/gradlew",
            "/home/my/new/project/gradle/wrapper/gradle-wrapper.properties",
            "/home/my/new/project/gradle/wrapper/gradle-wrapper.jar",
            "/home/my/new/project/signing/sample_private_key.pem",
            "/home/my/new/project/host/build.gradle"
        ).map(::Path)

        val transformed = TemplatePathTransformer(
            JavaPackage("com.megacorp"),
            Path("/home/template"),
            Path("/home/my/new/project"),
            JavaClass("MegaEnclave")
        ).transform(templateFiles)

        assertEquals(expected, transformed.toList())
    }
}
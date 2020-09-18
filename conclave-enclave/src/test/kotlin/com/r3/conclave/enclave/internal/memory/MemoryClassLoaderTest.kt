package com.r3.conclave.enclave.internal.memory

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections.singletonList

class MemoryClassLoaderTest {
    companion object {
        const val DATA_PATH: String = "/my/enclave"

        private val testJar = Paths.get(System.getProperty("conclave-enclave.test-jar"))
    }

    private lateinit var memoryURL: MemoryURL

    @BeforeEach
    fun setup() {
        memoryURL = URLSchemes.createMemoryURL(DATA_PATH, ByteBuffer.wrap(Files.readAllBytes(testJar)))
    }

    @AfterEach
    fun done() {
        URLSchemes.clearURLs()
    }

    @Test
    fun `memory class`() {
        with(MemoryClassLoader(singletonList(memoryURL), null)) {
            val enclaveConstraintClass = Class.forName("com.r3.conclave.client.EnclaveConstraint", false, this)
            assertEquals("com.r3.conclave.client.EnclaveConstraint", enclaveConstraintClass.name)
            assertEquals(this, enclaveConstraintClass.classLoader)
            assertEquals(memoryURL.value, enclaveConstraintClass.protectionDomain.codeSource.location)
            enclaveConstraintClass.getConstructor()

            val enclavePackage = enclaveConstraintClass.`package`
            assertNotNull(enclavePackage)
            assertEquals("com.r3.conclave.client", enclavePackage.name)
        }
    }

    @Test
    fun `memory class loader`() {
        with(MemoryClassLoader(singletonList(memoryURL))) {
            assertEquals(1, getURLs().size)
            assertEquals("memory:$DATA_PATH", getURLs()[0].toString())

            val resourceName = "com/r3/conclave/client/EnclaveConstraint.class"
            val resource = findResource(resourceName) ?: fail("Resource '$resourceName not found")
            assertEquals("memory:$DATA_PATH!/$resourceName", resource.toString())

            // Java 9+ does not guarantee that the application classloader is an instance of a URLClassLoader
            // so we can't just use (javaClass.classLoader as URLClassLoader).
            // Instead, build a new instance from the enclave jar
            val enclaveUrls = arrayOf(testJar.toUri().toURL())
            val urlLoader = URLClassLoader(enclaveUrls)

            val actualBytes = urlLoader.findResource(resourceName).readBytes()
            assertArrayEquals(actualBytes, resource.readBytes())

            val manifests = findResources("META-INF/MANIFEST.MF")
            assertTrue(manifests.hasMoreElements())
            assertEquals("$DATA_PATH!/META-INF/MANIFEST.MF", manifests.nextElement().path)
            assertFalse(manifests.hasMoreElements())
        }
    }
}

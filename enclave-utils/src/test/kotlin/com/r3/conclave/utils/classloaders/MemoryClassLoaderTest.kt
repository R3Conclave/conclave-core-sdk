package com.r3.conclave.utils.classloaders

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.net.URLClassLoader
import java.util.Collections.singletonList

class MemoryClassLoaderTest {
    companion object {
        const val DATA_PATH: String = "/my/enclave"

        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()
    }

    private lateinit var memoryURL: MemoryURL
    private lateinit var enclaveJar: File

    @BeforeEach
    fun setup() {
        enclaveJar = testEnclaves.getEnclaveJar(ShoutingEnclave::class.java)
        val enclaveData = enclaveJar.toByteBuffer()
        memoryURL = URLSchemes.createMemoryURL(DATA_PATH, enclaveData)
        System.gc()
    }

    @AfterEach
    fun done() {
        URLSchemes.clearURLs()
    }

    @Test
    fun `memory class`() {
        with(MemoryClassLoader(singletonList(memoryURL), null)) {
            val enclaveClass = Class.forName(ShoutingEnclave::class.java.name, false, this)
            assertEquals(ShoutingEnclave::class.java.name, enclaveClass.name)
            assertEquals(this, enclaveClass.classLoader)
            assertEquals(memoryURL.value, enclaveClass.protectionDomain.codeSource.location)
            enclaveClass.getDeclaredConstructor().newInstance()

            val enclavePackage = enclaveClass.`package`
            assertNotNull(enclavePackage)
            assertEquals(ShoutingEnclave::class.java.`package`.name, enclavePackage.name)
            assertEquals(enclaveClass.name.removeSuffix(".ShoutingEnclave"), enclavePackage.name)
        }
    }

    @Test
    fun `memory class loader`() {
        with(MemoryClassLoader(singletonList(memoryURL))) {
            assertEquals(1, getURLs().size)
            assertEquals("memory:$DATA_PATH", getURLs()[0].toString())

            val envClassName = "${EnclaveEnvironment::class.java.name.replace('.', '/')}.class"
            val resource = findResource(envClassName) ?: fail("Resource '$envClassName not found")
            assertEquals("memory:$DATA_PATH!/$envClassName", resource.toString())

            // Java 9+ does not guarantee that the application classloader is an instance of a URLClassLoader
            // so we can't just use (javaClass.classLoader as URLClassLoader).
            // Instead, build a new instance from the enclave jar
            val enclaveUrls = arrayOf(enclaveJar.toURI().toURL())
            val urlLoader = URLClassLoader(enclaveUrls)

            val actualBytes = urlLoader.findResource(envClassName).readBytes()
            assertArrayEquals(actualBytes, resource.readBytes())

            val manifests = findResources("META-INF/MANIFEST.MF")
            assertTrue(manifests.hasMoreElements())
            assertEquals("$DATA_PATH!/META-INF/MANIFEST.MF", manifests.nextElement().path)
            assertFalse(manifests.hasMoreElements())
        }
    }
}

class ShoutingEnclave : EnclaveCall, Enclave() {
    override fun invoke(bytes: ByteArray): ByteArray? = String(bytes).toUpperCase().toByteArray()
}

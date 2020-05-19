package com.r3.conclave.utils.classloaders

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.core.enclave.EnclaveApi
import com.r3.conclave.dynamictesting.TestEnclaves
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExpectedException
import java.net.URLClassLoader
import java.util.Collections.singletonList
import kotlin.test.fail

class MemoryClassLoaderTest {
    companion object {
        const val DATA_PATH: String = "/my/enclave"

        @ClassRule
        @JvmField
        val testEnclaves = TestEnclaves()
    }

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    private lateinit var memoryURL: MemoryURL

    @Before
    fun setup() {
        val enclaveJar = testEnclaves.getEnclaveJar(ShoutingEnclave::class.java)
        val enclaveData = enclaveJar.toByteBuffer()
        memoryURL = URLSchemes.createMemoryURL(DATA_PATH, enclaveData)
        System.gc()
    }

    @After
    fun done() {
        URLSchemes.clearURLs()
    }

    @Test
    fun testMemoryClass() {
        with(MemoryClassLoader(singletonList(memoryURL), null)) {
            val enclaveClass = Class.forName(ShoutingEnclave::class.java.name, false, this)
            assertEquals(ShoutingEnclave::class.java.name, enclaveClass.name)
            assertEquals(this, enclaveClass.classLoader)
            assertEquals(memoryURL.value, enclaveClass.protectionDomain.codeSource.location)
            enclaveClass.newInstance()

            val enclavePackage = enclaveClass.`package`
            assertNotNull(enclavePackage)
            assertEquals(ShoutingEnclave::class.java.`package`.name, enclavePackage.name)
            assertEquals(enclaveClass.name.removeSuffix(".ShoutingEnclave"), enclavePackage.name)
        }
    }

    @Test
    fun testMemoryClassLoader() {
        with(MemoryClassLoader(singletonList(memoryURL))) {
            assertEquals(1, getURLs().size)
            assertEquals("memory:$DATA_PATH", getURLs()[0].toString())

            val apiClassName = EnclaveApi::class.java.name.replace('.', '/') + ".class"
            val resource = findResource(apiClassName) ?: fail("Resource '$apiClassName not found")
            assertEquals("memory:$DATA_PATH!/$apiClassName", resource.toString())

            val actualBytes = (javaClass.classLoader as URLClassLoader).findResource(apiClassName).readBytes()
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

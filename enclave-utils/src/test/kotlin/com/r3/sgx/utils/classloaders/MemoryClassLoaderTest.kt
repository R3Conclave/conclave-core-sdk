package com.r3.sgx.utils.classloaders

import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.dynamictesting.TestEnclaves
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
        val enclaveJar = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
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
            val enclaveletClass = Class.forName(ShoutingEnclavelet::class.java.name, false, this)
            assertEquals(ShoutingEnclavelet::class.java.name, enclaveletClass.name)
            assertEquals(this, enclaveletClass.classLoader)
            assertEquals(memoryURL.value, enclaveletClass.protectionDomain.codeSource.location)
            enclaveletClass.newInstance()

            val enclaveletPackage = enclaveletClass.`package`
            assertNotNull(enclaveletPackage)
            assertEquals(ShoutingEnclavelet::class.java.`package`.name, enclaveletPackage.name)
            assertEquals(enclaveletClass.name.removeSuffix(".ShoutingEnclavelet"), enclaveletPackage.name)
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

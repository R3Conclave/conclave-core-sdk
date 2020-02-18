package com.r3.sgx.utils.classloaders

import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.SgxReportData
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.core.enclave.internal.NativeEnclaveApi.ENCLAVE_CLASS_ATTRIBUTE_NAME
import com.r3.sgx.dynamictesting.TestEnclaves
import com.r3.sgx.testing.HelperUtilities.expectWithin
import org.junit.After
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.IOException
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.util.function.Supplier
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarInputStream
import java.util.jar.Manifest

class MemoryURLStreamHandlerTest {
    companion object {
        const val DATA_PATH: String = "/my/enclave"
        const val data: String = "Wibble!"
        const val EOF = -1

        @ClassRule
        @JvmField
        val testEnclaves = TestEnclaves()
    }

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @After
    fun done() {
        URLSchemes.clearURLs()
    }

    @Test
    fun testMemoryURL() {
        val (url, urlLock) = URLSchemes.createMemoryURL(DATA_PATH, ByteBuffer.wrap(data.toByteArray()))
        assertEquals("memory:/my/enclave", url.toString())
        assertEquals("memory:/my/enclave", urlLock)
        assertNotSame(url.toString(), urlLock)
        url.openConnection().apply {
            assertEquals("application/octet-stream", contentType)
            assertEquals(data.length, contentLength)
            assertFalse(allowUserInteraction)
            assertFalse(doOutput)
            assertFalse(useCaches)
            assertTrue(doInput)
        }
    }

    @Test
    fun testExistingMemoryURL() {
        exception.expect(MalformedURLException::class.java)
        exception.expectMessage("URL 'memory:/my/enclave' already exists")

        URLSchemes.createMemoryURL(DATA_PATH, ByteBuffer.wrap(byteArrayOf()))
        URLSchemes.createMemoryURL(DATA_PATH, ByteBuffer.wrap(data.toByteArray()))
    }

    class ExampleEnclavelet : Enclavelet() {
        override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
            return Cursor.allocate(SgxReportData)
        }

        override fun createHandler(api: EnclaveApi): Handler<*> {
            throw UnsupportedOperationException("Does not exist")
        }
    }

    @Test
    fun testStreamForJarInMemory() {
        val enclaveJar = testEnclaves.getEnclaveJar(ExampleEnclavelet::class.java)
        val enclaveData = enclaveJar.toByteBuffer()
        val (memoryURL, _) = URLSchemes.createMemoryURL(DATA_PATH, enclaveData)
        System.gc()
        JarInputStream(memoryURL.openStream()).use { jar ->
            val manifest: Manifest = jar.manifest
            assertEquals("1.0", manifest.mainAttributes.getValue(MANIFEST_VERSION))
            assertNotNull(manifest.mainAttributes.getValue(ENCLAVE_CLASS_ATTRIBUTE_NAME))
        }
    }

    @Test
    fun testClassLoaderForJarInMemory() {
        val enclaveJar = testEnclaves.getEnclaveJar(ExampleEnclavelet::class.java)
        val enclaveData = enclaveJar.toByteBuffer()
        val (memoryURL, _) = URLSchemes.createMemoryURL(DATA_PATH, enclaveData)
        System.gc()
        URLClassLoader(arrayOf(memoryURL), null).use { cl ->
            val exampleClass = Class.forName(ExampleEnclavelet::class.java.name, false, cl)
            assertEquals(ExampleEnclavelet::class.java.name, exampleClass.name)
            assertEquals(cl, exampleClass.classLoader)
            assertEquals(memoryURL, exampleClass.protectionDomain.codeSource.location)
        }
    }

    @Test
    fun testUnusedJarDataIsEvictedFromMemory() {
        exception.expect(IOException::class.java)
        exception.expectMessage("No data for URL 'memory:/my/enclave")

        val enclaveJar = testEnclaves.getEnclaveJar(ExampleEnclavelet::class.java)
        val enclaveData = enclaveJar.toByteBuffer()
        // Ensure that we discard the strong reference key for our
        // URL data so that the garbage collector can reap it.
        val memoryURL = URLSchemes.createMemoryURL(DATA_PATH, enclaveData).value
        System.gc()
        expectWithin(seconds = 10, condition = Supplier { URLSchemes.size == 0 })
        memoryURL.openConnection()
    }

    @Suppress("UsePropertyAccessSyntax")
    @Test
    fun testReadOnlyMemoryBufferCannotBeModifiedAccidentally() {
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES).let { buf ->
            buf.putInt(1).putInt(999).flip()
            buf.asReadOnlyBuffer()
        }
        assertTrue(buffer.isReadOnly)

        val (memoryURL, _) = URLSchemes.createMemoryURL(DATA_PATH, buffer)
        assertEquals(1, buffer.getInt())
        assertEquals(1, (memoryURL.content as ByteBuffer).getInt())
    }

    @Suppress("UsePropertyAccessSyntax")
    @Test
    fun testWritableMemoryBufferCannotBeModifiedAccidentally() {
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES)
            .putInt(1).putInt(999)
        buffer.flip()
        assertFalse(buffer.isReadOnly)

        val (memoryURL, _) = URLSchemes.createMemoryURL(DATA_PATH, buffer)
        assertEquals(1, buffer.getInt())
        assertEquals(1, (memoryURL.content as ByteBuffer).getInt())
    }

    @Test
    fun testReadingEmptyBuffer() {
        val buffer = ByteBuffer.allocate(0)
        val (memoryURL, _) = URLSchemes.createMemoryURL(DATA_PATH, buffer)
        assertEquals(EOF, memoryURL.openStream().read())
        assertEquals(EOF, memoryURL.openStream().read(ByteArray(1)))
    }
}

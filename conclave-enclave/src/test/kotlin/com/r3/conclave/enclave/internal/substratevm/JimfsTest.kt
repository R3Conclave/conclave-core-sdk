package com.r3.conclave.enclave.internal.substratevm

import com.r3.conclave.enclave.internal.substratevm.mock.MockCCharPointer
import com.r3.conclave.filesystem.jimfs.JimfsFileSystemProvider
import com.r3.conclave.filesystem.jimfs.SystemJimfsFileSystemProvider
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.nativeimage.c.type.CTypeConversion
import org.graalvm.word.PointerBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.ScopedMock
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.FileSystems

abstract class JimfsTest {
    companion object {
        val data = byteArrayOf(0, 1, 2, 3)
        const val file = "/file.data"
        const val expectedFd = 16
        private var bufferInitializationIteration = 0
        private val mocks = mutableListOf<ScopedMock>()

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertThat(JimfsFileSystemProvider.instance()).isNotNull

            val fileSystemsMock = Mockito.mockStatic(FileSystems::class.java)
            fileSystemsMock.`when`<FileSystem> {
                FileSystems.getDefault()
            }.thenReturn(SystemJimfsFileSystemProvider.fileSystems[URI.create("file:${JimfsFileSystemProvider.DEFAULT_FILE_SYSTEM_PATH}")])

            val cTypeConversionMock = Mockito.mockStatic(CTypeConversion::class.java)
            cTypeConversionMock.`when`<String> {
                CTypeConversion.toJavaString(any(MockCCharPointer::class.java))
            }.thenAnswer { invocation ->
                val mockCCharPointer = invocation.arguments[0] as MockCCharPointer
                String(mockCCharPointer.byteArray)
            }

            cTypeConversionMock.`when`<ByteBuffer> {
                CTypeConversion.asByteBuffer(any(PointerBase::class.java), any(Int::class.java))
            }.thenAnswer { initializeByteBuffer() }

            mocks.add(fileSystemsMock)
            mocks.add(cTypeConversionMock)
        }

        private fun initializeByteBuffer(): ByteBuffer {
            val buffer = ByteBuffer.allocate(data.size).put(upcomingValues())
            bufferInitializationIteration++
            buffer.rewind()
            return buffer
        }

        fun upcomingValues(): ByteArray {
            return ByteArray(data.size) { (it + bufferInitializationIteration * 10).toByte() }
        }

        @AfterAll
        @JvmStatic
        fun closeMocks() {
            mocks.forEach {
                it.close()
            }
            mocks.clear()
        }
    }
}
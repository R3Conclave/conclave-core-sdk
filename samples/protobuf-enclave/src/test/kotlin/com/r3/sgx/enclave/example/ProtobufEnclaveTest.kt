package com.r3.sgx.enclave.example

import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.core.host.NativeHostApi
import com.r3.sgx.core.host.internal.Native
import com.r3.sgx.testing.RootHandler
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtobufEnclaveTest {
    private val enclavePath = System.getProperty("com.r3.sgx.enclave.path")

    class OcallRecordingHost : SimpleProtoHandler<ExampleOcall, ExampleEcall>(ExampleOcall.parser()) {
        val ocalls = ArrayList<ExampleOcall>()
        override fun onReceive(connection: ProtoSender<ExampleEcall>, message: ExampleOcall) {
            ocalls.add(message)
        }
    }

    class GetMeasurementHost : BytesHandler() {
        var measurement: ByteCursor<SgxMeasurement>? = null
        override fun onReceive(connection: Connection, input: ByteBuffer) {
            measurement = Cursor(SgxMeasurement, input)
        }
    }

    class Host(enclave: File) {
        val ocallRecordingHost = OcallRecordingHost()
        val getMeasurementHost = GetMeasurementHost()
        val root = NativeHostApi(EnclaveLoadMode.SIMULATION).createEnclave(RootHandler(), enclave).connection
        val ocallRecordingSender = root.addDownstream(ocallRecordingHost)
        val getMeasurementSender = root.addDownstream(getMeasurementHost)
    }

    @Test
    fun nativeEnclaveWorks() {
        val host = Host(File(enclavePath))
        val hello1 = ExampleEcall.newBuilder()
                .setMessage("StringFromHost1")
                .build()
        host.ocallRecordingSender.send(hello1)
        assertEquals(1, host.ocallRecordingHost.ocalls.size)
        assertEquals("First call ${hello1.message}", host.ocallRecordingHost.ocalls[0].message)

        val hello2 = ExampleEcall.newBuilder()
                .setMessage("StringFromHost2")
                .build()
        host.ocallRecordingSender.send(hello2)
        assertEquals(2, host.ocallRecordingHost.ocalls.size)
        assertEquals("Second call ${hello2.message}", host.ocallRecordingHost.ocalls[1].message)

        assertFailsWith(RuntimeException::class) {
            host.ocallRecordingSender.send(hello2)
        }
    }

    @Test
    fun canGetMeasurementOfEnclave() {
        val host = Host(File(enclavePath))
        host.getMeasurementSender.send(ByteBuffer.allocate(0))
        val measurementFromEnclave = host.getMeasurementHost.measurement ?: throw IllegalStateException("Didn't get measurement")
        val metadata = Cursor.allocate(SgxMetadata)
        Native.getMetadata(enclavePath, metadata.getBuffer().array())
        val measurementFromHost = metadata[SgxMetadata.enclaveCss][SgxEnclaveCss.body][SgxCssBody.measurement]
        assertEquals(measurementFromEnclave, measurementFromHost)
    }
}

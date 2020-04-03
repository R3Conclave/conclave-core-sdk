package com.r3.sgx.enclave.example

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxReport
import com.r3.conclave.common.internal.SgxReportBody
import com.r3.sgx.core.common.BytesHandler
import com.r3.sgx.core.common.ProtoSender
import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.common.SimpleProtoHandler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.RootEnclave
import java.nio.ByteBuffer

class ProtobufEnclave : RootEnclave() {
    override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        mux.addDownstream(ExampleHandler())
        mux.addDownstream(GetMeasurement(api))
    }

    class GetMeasurement(val api: EnclaveApi) : BytesHandler() {
        override fun onReceive(connection: BytesHandler.Connection, input: ByteBuffer) {
            val report = Cursor.allocate(SgxReport)
            api.createReport(null, null, report.getBuffer().array())
            val measurement = report[SgxReport.body][SgxReportBody.measurement].read()
            connection.send(measurement)
        }
    }

    class ExampleHandler : SimpleProtoHandler<ExampleEcall, ExampleOcall>(ExampleEcall.parser()) {
        private var ecallCount = 0
        override fun onReceive(connection: ProtoSender<ExampleOcall>, message: ExampleEcall) {
            when (ecallCount) {
                0 -> {
                    val ocall = ExampleOcall.newBuilder()
                            .setMessage("First call ${message.message}")
                            .build()
                    connection.send(ocall)
                }
                1 -> {
                    val ocall = ExampleOcall.newBuilder()
                            .setMessage("Second call ${message.message}")
                            .build()
                    connection.send(ocall)
                }
                else -> {
                    throw IllegalStateException("Only expected 2 ecalls")
                }
            }
            ecallCount++
        }
    }
}

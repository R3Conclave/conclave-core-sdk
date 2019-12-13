package com.r3.sgx.core.enclave

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.*
import java.nio.ByteBuffer

/**
 * The enclave side of the EPID attestation protocol. It receives report requests from the host, containing the target
 * Quoting Enclave's information, and sends back a report.
 */
abstract class EpidAttestationEnclaveHandler(val api: EnclaveApi) : SimpleProtoHandler<EpidHostMessage, EpidEnclaveMessage>(EpidHostMessage.parser()) {
    abstract val reportData: Cursor<ByteBuffer, SgxReportData>

    override fun onReceive(connection: ProtoSender<EpidEnclaveMessage>, message: EpidHostMessage) {
        when {
            message.hasGetReportRequest() -> {
                val qeTargetInfo = message.getReportRequest.quotingEnclaveTargetInfo.toByteArray()
                val report = Cursor.allocate(SgxReport)
                api.createReport(qeTargetInfo, reportData.getBuffer().array(), report.getBuffer().array())
                val reply = EpidEnclaveMessage.newBuilder()
                        .setGetReportReply(GetReportReply.newBuilder()
                                .setReport(ByteString.copyFrom(report.getBuffer())))
                        .build()
                connection.send(reply)
            }
            else -> {
                throw IllegalStateException()
            }
        }
    }
}

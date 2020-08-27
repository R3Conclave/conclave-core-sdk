package com.r3.conclave.integrationtests.kotlin.enclave

import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail

class KotlinEnclave : Enclave(), EnclaveCall {
    override fun invoke(bytes: ByteArray): ByteArray? {
        return callUntrustedHost(bytes + 1) { fromHost ->
            fromHost + 2
        }
    }

    override fun receiveMail(id: Long, mail: EnclaveMail) {
        val fromHost = callUntrustedHost(mail.bodyAsBytes.reversedArray())!!
        val response = createMail(mail.authenticatedSender!!, fromHost)
        response.topic = mail.topic
        postMail(response, null)
    }
}

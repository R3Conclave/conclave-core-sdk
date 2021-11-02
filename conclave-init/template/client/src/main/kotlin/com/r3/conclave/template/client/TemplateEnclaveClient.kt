package com.r3.conclave.template.client

import com.r3.conclave.client.EnclaveClient
import com.r3.conclave.client.web.WebEnclaveTransport
import com.r3.conclave.common.EnclaveConstraint
import kotlin.system.exitProcess

object TemplateEnclaveClient {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 1) {
            println("Usage: client/bin/client ENCLAVE_CONSTRAINT")
            exitProcess(1)
        }
        val constraint = EnclaveConstraint.parse(args.single())

        WebEnclaveTransport("http://localhost:8080").use { transport ->
            EnclaveClient(constraint).use { client ->
                client.start(transport)
                val responseMail = client.sendMail("abc".toByteArray())
                val responseString = responseMail?.let { String(it.bodyAsBytes) }
                println("Enclave returned '$responseString'")
            }
        }
    }
}
package com.r3.conclave.template.enclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail

class TemplateEnclaveKotlin : Enclave() {
    override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
        val response = postOffice(mail).encryptMail("321".toByteArray())
        postMail(response, "response")
    }
}
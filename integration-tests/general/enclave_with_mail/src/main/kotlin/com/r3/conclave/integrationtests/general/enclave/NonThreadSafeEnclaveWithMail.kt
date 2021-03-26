package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import java.util.concurrent.atomic.AtomicInteger

class NonThreadSafeEnclaveWithMail : Enclave() {

    private val hostCalls = AtomicInteger(0)
    private val mailCalls = AtomicInteger(0)

    override fun getThreadSafe(): Boolean {
        return false
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        check(hostCalls) {
            callUntrustedHost(bytes)
        }
        return null
    }

    override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
        check(mailCalls) {
            val mailBytes = postOffice(enclaveInstanceInfo).encryptMail(byteArrayOf())
            postMail(mailBytes, "self")
        }
    }

    private fun check(atomicInteger: AtomicInteger, block: () -> Unit) {
        val x = atomicInteger.incrementAndGet()
        if (x > 1)
            throw IllegalStateException("All calls should be serialized by default: $x")
        Thread.sleep(100)
        block()
        atomicInteger.decrementAndGet()
    }
}


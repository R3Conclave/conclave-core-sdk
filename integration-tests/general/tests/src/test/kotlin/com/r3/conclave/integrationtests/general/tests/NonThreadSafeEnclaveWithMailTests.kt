package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.threadWithFuture
import com.r3.conclave.mail.Curve25519PrivateKey
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class NonThreadSafeEnclaveWithMailTests : JvmTest("com.r3.conclave.integrationtests.general.enclave.NonThreadSafeEnclaveWithMail") {
    @Disabled("broken in HW mode: https://r3-cev.atlassian.net/browse/CON-303")
    @Test
    fun notMultiThreadedByDefault() {
        // Run a bunch of threads through the enclave. They will check that only one thread is inside
        // receiveFromUntrustedHost at once and throw if not.
        (0..10).map {
            threadWithFuture {
                enclaveHost.callEnclave(byteArrayOf()) {
                    // Pause to give other threads a time to start and try to enter the enclave.
                    // Not ideal to use Thread.sleep in a test of course, however, we are trying to prove that
                    // they will NOT enter, and we can't tell the difference here between "didn't enter because
                    // they are still starting" and "didn't enter because they hit the lock". There's probably
                    // a better way to do this.
                    Thread.sleep(100)
                    null
                }
            }
        }.forEach { it.join() }

        // Now do it again but with mail.
        (0..10).map {
            val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice(Curve25519PrivateKey.random(), it.toString())
            threadWithFuture {
                enclaveHost.deliverMail(it.toLong(), postOffice.encryptMail(byteArrayOf()), null) {
                    null
                }
            }
        }.forEach { it.join() }
    }
}
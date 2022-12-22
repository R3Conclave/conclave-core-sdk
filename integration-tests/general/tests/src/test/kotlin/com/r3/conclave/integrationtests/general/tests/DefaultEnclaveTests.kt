package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.CheckNotMultiThreadedAction
import com.r3.conclave.integrationtests.general.common.tasks.Echo
import com.r3.conclave.integrationtests.general.common.tasks.EchoWithCallback
import com.r3.conclave.integrationtests.general.common.tasks.PutPersistentMap
import com.r3.conclave.integrationtests.general.common.threadWithFuture
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class DefaultEnclaveTests : AbstractEnclaveActionTest() {
    @Test
    fun `not multi-threaded by default`() {
        // Run a bunch of threads through the enclave. They will check that only one thread is inside
        // receiveFromUntrustedHost at once and throw if not.
        (0..10).map {
            threadWithFuture {
                callEnclave(CheckNotMultiThreadedAction()) {
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
            val mailClient = newMailClient()
            threadWithFuture {
                mailClient.deliverMail(CheckNotMultiThreadedAction()) {
                    Thread.sleep(100)
                    null
                }
            }
        }.forEach { it.join() }
    }

    @Test
    fun `persistent map disabled by default`() {
        val persistentMapAction = PutPersistentMap("Good morning!", "What's so good about it, eh?".toByteArray())
        val mailClient = newMailClient()
        val e = assertThrows<IllegalStateException> { mailClient.deliverMail(persistentMapAction) }
        assertThat(e).hasMessageStartingWith("The enclave persistent map is not enabled.")
    }

    @Test
    fun `test JNI memory leaks`() {
        val echo = Echo(ByteArray(16*1024) { (it % 256).toByte() })
        for (n in 1..1000) {
            callEnclave(echo)
        }
    }

    @Test
    fun `destroy in OCALL`() {
        graalvmOnlyTest() // CON-1262: Make sure EnclaveHost.close() cannot be called inside a callback
        val echo = EchoWithCallback(byteArrayOf())
        var called = false
        assertThatThrownBy {
            callEnclave(echo) {
                called = true
                enclaveHost().close()
                null
            }
        }
        assertTrue(called, "Ocall must be called")
    }

    @Test
    fun `large ECALL and OCALL`() {
        val largePayload = Random.nextBytes(1024 * 1024)
        val response = callEnclave(Echo(largePayload))
        assertThat(largePayload.contentEquals(response)).isTrue
    }
}

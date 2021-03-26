package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.Sum1ToN
import com.r3.conclave.integrationtests.general.common.tasks.Wait
import com.r3.conclave.integrationtests.general.common.tasks.threadWithFuture
import com.r3.conclave.integrationtests.general.common.tasks.toByteArray
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ThreadSafeEnclaveTests : JvmTest(threadSafe = true) {

    @Test
    fun `TCS reallocation`() {
        repeat(3) {
            // using atomic here due to the bug with synchronized block
            // https://r3-cev.atlassian.net/browse/CON-345
            val callCount = AtomicInteger(0)
            val responses = { _: ByteArray ->
                callCount.incrementAndGet()
                null
            }

            val futures = (1..Wait.PARALLEL_ECALLS).map { n ->
                threadWithFuture {
                    val wait = Wait(n.toByteArray())
                    sendMessage(wait, responses)
                }
            }
            futures.forEach { it.join() }
            assertThat(callCount.get()).isEqualTo(futures.size)
        }
    }

    @Test
    fun `threading in enclave`() {
        val n = 8 // <= defaultTCSNum(10)
        val calc = Sum1ToN(n)
        val result = sendMessage(calc)
        assertThat(result).isEqualTo((n * (n + 1)) / 2)
    }

    @Disabled(": CON-360")
    // fatal error 'PosixJavaThreads.start0: pthread_create'
    @Test
    fun `exception is thrown if too many threads are requested`() {
        val n = 15 // > defaultTCSNum(10)
        val calc = Sum1ToN(n)
        assertThatThrownBy {
            sendMessage(calc)
        }.hasMessageContaining("The enclave ran out of TCS slots when calling from a new thread into the enclave.") // SGX_ERROR_OUT_OF_TCS

        closeHost = false
    }
}
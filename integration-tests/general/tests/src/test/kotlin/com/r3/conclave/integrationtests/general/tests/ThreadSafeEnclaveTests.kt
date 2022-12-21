package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.integrationtests.general.common.tasks.*
import com.r3.conclave.integrationtests.general.common.threadWithFuture
import com.r3.conclave.integrationtests.general.common.toByteArray
import com.r3.conclave.integrationtests.general.common.toInt
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.IntStream
import kotlin.concurrent.withLock
import kotlin.streams.toList

class ThreadSafeEnclaveTests : AbstractEnclaveActionTest("com.r3.conclave.integrationtests.general.threadsafeenclave.ThreadSafeEnclave") {
    @Test
    fun `concurrent calls into the enclave`() {
        val n = 1000
        callEnclave(SetMaxCallCount(n))

        val sum = IntStream.rangeClosed(1, n)
            .parallel()
            .mapToObj { callEnclave(ConcurrentCallsIntoEnclaveAction(it)) }
            .toList()
            .single { it > 0 }
        assertThat(sum).isEqualTo((n * (n + 1)) / 2)
    }

    @Test
    fun `concurrent calls into the enclave with callbacks`() {
        val n = 100
        val sums = IntStream.rangeClosed(1, n)
            .parallel()
            .map { i ->
                var sum = 0
                callEnclave(RepeatedOcallsAction(i)) {
                    sum += it.toInt() + 1
                    null
                }
                sum
            }
            .toList()
        assertThat(sums).isEqualTo((1..n).map { (it * (it + 1)) / 2 })
    }

    @Test
    fun `TCS allocation policy`() {
        val tcs = 10
        // launch #of host threads exceeding TCSNum
        val lock = ReentrantLock()
        // Check that TCS are NOT by default bound to application threads
        val concurrentCalls = tcs + 20
        val ongoing = CountDownLatch(concurrentCalls)

        val futures = (1..concurrentCalls).map {
            threadWithFuture {
                lock.withLock {
                    val response = callEnclave(Increment(it))
                    assertThat(response).isEqualTo(it + 1)
                }
                ongoing.countDown()
                ongoing.await()
            }
        }
        futures.forEach { it.join() }
    }

    @Test
    fun `TCS reallocation`() {
        repeat(3) {
            val callCount = AtomicInteger(0)

            val futures = (1..TcsReallocationAction.PARALLEL_ECALLS).map { n ->
                threadWithFuture {
                    callEnclave(TcsReallocationAction(n.toByteArray())) {
                        callCount.incrementAndGet()
                        null
                    }
                }
            }
            futures.forEach { it.join() }
            assertThat(callCount.get()).isEqualTo(futures.size)
        }
    }

    @Test
    fun `threading inside enclave`() {
        //  For Gramine, we want this test to run only in SIMULATION mode
        // CON-1270: Requesting too many threads is failing in Gramine
        if (TestUtils.RuntimeType.GRAMINE.name == System.getProperty("runtimeType").uppercase()) {
            assumeTrue(EnclaveMode.SIMULATION.name == System.getProperty("enclaveMode").uppercase())
        }

        val n = 38 // <= thread safe enclave maxThreads (40)
        val result = callEnclave(TooManyThreadsRequestedAction(n))
        assertThat(result).isEqualTo((n * (n + 1)) / 2)
    }

    @Test
    fun `exception is thrown if too many threads are requested`() {
        graalvmOnlyTest() // CON-1270: Requesting too many threads test is failing in Gramine

        // This test hangs when tearing down the enclave, so we disable teardown here.
        // TODO: CON-1244 Figure out why this test hangs, then remove this teardown logic.
        doEnclaveTeardown = false
        val n = 45 // > thread safe enclave maxThreads (40)
        assertThatThrownBy {
            callEnclave(TooManyThreadsRequestedAction(n))
        }.hasMessageContaining("The enclave ran out of TCS slots when calling from a new thread into the enclave.") // SGX_ERROR_OUT_OF_TCS
    }
}

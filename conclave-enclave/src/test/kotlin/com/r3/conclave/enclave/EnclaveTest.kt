package com.r3.conclave.enclave

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.dynamictesting.EnclaveBuilder
import com.r3.conclave.dynamictesting.EnclaveConfig
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.kotlin.callEnclave
import com.r3.conclave.testing.RecordingEnclaveCall
import com.r3.conclave.testing.threadWithFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class EnclaveTest {
    companion object {
        private val testEnclaves = TestEnclaves()

        @BeforeAll
        @JvmStatic
        fun before() {
            testEnclaves.before()
        }

        @AfterAll
        @JvmStatic
        fun after() {
            testEnclaves.after()
        }
    }

    private var closeHost: Boolean = false
    private lateinit var host: EnclaveHost

    @AfterEach
    fun cleanUp() {
        if (closeHost) {
            host.close()
        }
    }

    @Test
    fun `TCS allocation policy`() {
        val tcs = 20
        start<IncrementingEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(tcs)))
        val lock = ReentrantLock()
        // Check that TCS are NOT by default bound to application threads
        val concurrentCalls = tcs + 20
        val ongoing = CountDownLatch(concurrentCalls)
        val futures = (1..concurrentCalls).map {
            threadWithFuture {
                lock.withLock {
                    val response = host.callEnclave(it.toByteArray())!!
                    assertThat(response.toInt()).isEqualTo(it + 1)
                }
                ongoing.countDown()
                ongoing.await()
            }
        }

        futures.forEach { it.join() }
    }

    @Disabled("https://r3-cev.atlassian.net/browse/CON-88")
    @Test
    fun `TCS reallocation`() {
        val tcs = WaitingEnclave.PAR_ECALLS + 3 // Some TCS are reserved for Avian internal threads
        start<WaitingEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(tcs)))
        repeat (3) {
            val responses = RecordingEnclaveCall()
            val futures = (1..WaitingEnclave.PAR_ECALLS).map {
                threadWithFuture {
                    host.callEnclave(it.toByteArray(), responses)
                }
            }
            futures.forEach { it.join() }
            assertThat(responses.calls).hasSameSizeAs(futures)
        }
    }

    @Test
    fun `threading in enclave`() {
        val n = 15
        start<ThreadingEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(20)))
        val response = host.callEnclave(n.toByteArray())!!
        assertThat(response.toInt()).isEqualTo((n * (n + 1)) / 2)
    }

    @Test
    fun `exception is thrown if too many threads are requested`() {
        val n = 15
        start<ThreadingEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(10)))
        assertThatThrownBy {
            host.callEnclave(n.toByteArray())
        }.hasMessageContaining("SGX_ERROR_OUT_OF_TCS")
        // Note: enclaveHandle.destroy hangs due to inconsistent internal Avian thread state after SGX_ERROR_OUT_OF_TCS,
        // so we cant properly shutdown in this case
        closeHost = false
    }

    @Test
    fun `test JNI memory leaks`() {
        start<EchoEnclave>()
        val message = ByteArray(500000) { (it % 256).toByte() }
        for (n in 1..1000) {
            host.callEnclave(message)
        }
    }

    @Test
    fun `destroy while OCALL in progress`() {
        start<EchoCallbackEnclave>()
        val semaphore = CompletableFuture<Unit>()
        val callback = object : EnclaveCall {
            val ocalls = AtomicInteger(0)
            override fun invoke(bytes: ByteArray): ByteArray? {
                return if (ocalls.getAndIncrement() == 0) {
                    semaphore.get()
                    bytes
                } else {
                    null
                }
            }
        }
        val ecall = threadWithFuture {
            host.callEnclave(ByteArray(16), callback)
        }
        while (callback.ocalls.get() == 0) {
            Thread.sleep(100)
        }
        val destructor = threadWithFuture {
            host.close()
        }
        semaphore.complete(Unit)
        destructor.join()
        ecall.join()
        assertThat(callback.ocalls.get()).isEqualTo(2)
    }

    @Disabled("This test demonstrates the waiting behaviour of enclave destruction")
    @Test
    fun `destroy while ECALL in progress`() {
        start<SpinningEnclave>()
        val recorder = RecordingEnclaveCall()
        thread(isDaemon = true) {
            host.callEnclave(byteArrayOf(), recorder)
        }
        while (recorder.calls.isEmpty()) {
            // Wait until the enclave signals the ECALL is in progress
            Thread.sleep(1)
        }
        host.close() // hang
    }

    @Test
    fun `destroy in OCALL`() {
        start<EchoCallbackEnclave>()
        var called = false
        assertThatThrownBy {
            host.callEnclave(byteArrayOf()) {
                called = true
                host.close()
                null
            }
        }
        assertTrue(called, "Ocall must be called")
    }

    @Test
    fun `child thread can do OCALLs`() {
        start<ChildThreadSendingEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(10)))
        val recorder = RecordingEnclaveCall()
        host.callEnclave(byteArrayOf(), recorder)
        assertThat(recorder.calls.single()).isEqualTo("test".toByteArray())
    }

    private inline fun <reified T : Enclave> start(enclaveBuilder: EnclaveBuilder = EnclaveBuilder()) {
        host = testEnclaves.hostTo<T>(enclaveBuilder).apply {
            start(null, null)
        }
        closeHost = true
    }

    class EchoEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = bytes
    }

    class EchoCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            var echoBack = bytes
            while (true) {
                val response = callUntrustedHost(echoBack) ?: return null
                echoBack = response
            }
        }
    }

    class IncrementingEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            val n = bytes.toInt()
            return (n + 1).toByteArray()
        }
    }

    class WaitingEnclave : EnclaveCall, Enclave() {
        companion object {
            const val PAR_ECALLS = 16
        }

        private val ecalls = AtomicInteger(0)
        private val ocalls = AtomicInteger(0)

        override fun invoke(bytes: ByteArray): ByteArray? {
            ecalls.incrementAndGet()
            while (ecalls.get() < PAR_ECALLS) {
                // Wait
            }
            synchronized(this) {
                callUntrustedHost(bytes)
            }
            if (ocalls.incrementAndGet() == PAR_ECALLS) {
                ecalls.set(0)
                ocalls.set(0)
            }
            return null
        }
    }

    class ThreadingEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            val n = bytes.toInt()
            val latchBefore = CountDownLatch(n)
            val latchAfter = CountDownLatch(n)
            val sum = AtomicInteger(0)
            val semaphore = Semaphore(0)
            try {
                for (i in 1..n) {
                    thread {
                        latchBefore.countDown()
                        semaphore.acquire()
                        sum.addAndGet(i)
                        latchAfter.countDown()
                    }
                }
                latchBefore.await() // wait until all threads are started
                semaphore.release(n) // unblock threads
                latchAfter.await() // wait until all threads finished adding
                return sum.get().toByteArray()
            } finally {
                semaphore.release(n) // in case an exception occurred before releasing the semaphore
            }
        }
    }

    class SpinningEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            callUntrustedHost(bytes)
            while (true) {
                // Spin
            }
        }
    }

    class ChildThreadSendingEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            threadWithFuture {
                callUntrustedHost("test".toByteArray())
            }.join()
            return null
        }
    }

}

private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

private fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).getInt()

package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class NonThreadSafeEnclaveTests : JvmTest(threadSafe = false) {
    private val defaultTCSNum = 10

    @Test
    fun `TCS allocation policy`() {
        // launch #of host threads exceeding TCSNum
        val lock = ReentrantLock()
        val concurrentCalls = defaultTCSNum + 20
        val ongoing = CountDownLatch(concurrentCalls)

        val futures = (1..concurrentCalls).map {
            threadWithFuture {
                lock.withLock {
                    val inc = Increment(it)
                    val response = sendMessage(inc)
                    assertThat(response).isEqualTo(it + 1)
                }
                ongoing.countDown()
                ongoing.await()
            }
        }
        futures.forEach { it.join() }
    }

    @Test
    fun `test JNI memory leaks`() {
        val echo = Echo(ByteArray(16*1024) { (it % 256).toByte() })
        for (n in 1..1000) {
            sendMessage(echo)
        }
    }

    @Test
    fun `destroy while OCALL in progress`() {
        val echo = EchoWithCallback(ByteArray(16))
        val semaphore = CompletableFuture<Unit>()
        val callback = object: (ByteArray) -> ByteArray? {
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
            sendMessage(echo, callback)
        }
        while (callback.ocalls.get() == 0) {
            Thread.sleep(100)
        }
        val destructor = threadWithFuture {
            enclaveHost.close()
        }
        semaphore.complete(Unit)
        destructor.join()
        ecall.join()
        assertThat(callback.ocalls.get()).isEqualTo(2)
        closeHost = false
    }

    @Disabled("This test demonstrates the waiting behaviour of enclave destruction: CON-361")
    @Test
    fun `destroy while ECALL in progress`() {
        val spin = Spin(byteArrayOf())

        val callCount = AtomicInteger(0)
        val responses = { _: ByteArray ->
            callCount.incrementAndGet()
            null
        }

        thread(isDaemon = true) {
            sendMessage(spin, responses)
        }

        while (callCount.get() == 0) {
            // Wait until the enclave signals the ECALL is in progress
            Thread.sleep(1)
        }

        closeHost = false
        enclaveHost.close() // hang
    }

    @Test
    fun `destroy in OCALL`() {
        val echo = EchoWithCallback(byteArrayOf())
        var called = false
        assertThatThrownBy {
            sendMessage(echo) {
                called = true
                enclaveHost.close()
                null
            }
        }

        closeHost = !called
        assertTrue(called, "Ocall must be called")
    }

    @Disabled("https://r3-cev.atlassian.net/browse/CON-100, CON-362")
    @Test
    fun `child thread can do OCALLs`() {
        val child = ChildThreadSending()
        val recorder = RecordingCallback()
        sendMessage(child, recorder)
        assertThat(recorder.calls.single()).isEqualTo("test".toByteArray())
    }
}

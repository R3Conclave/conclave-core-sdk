package com.r3.sgx.core.enclave

import com.r3.sgx.core.common.BytesHandler
import com.r3.sgx.dynamictesting.EnclaveBuilder
import com.r3.sgx.dynamictesting.EnclaveConfig
import com.r3.sgx.dynamictesting.EnclaveTestMode
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class EnclaveApiTest : TestEnclavesBasedTest(mode = EnclaveTestMode.Native) {

    @Rule
    @JvmField
    val exception: ExpectedException = ExpectedException.none()

    @Test
    fun simpleEchoWorks() {
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), EchoEnclave::class.java, block = Consumer { enclaveHandle ->
            val input = "Hello"
            val connection = enclaveHandle.connection.addDownstream(handler)
            connection.send(input)
            assertEquals(1, handler.calls.size)
            assertEquals(input, handler.calls[0])
        })
    }

    class EnclaveNameReadingEnclave : StringEnclave() {
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            val enclaveClass = api.getEnclaveClassName()
            sender.send(enclaveClass)
        }
    }

    @Test
    fun canReadOurOwnManifest() {
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), EnclaveNameReadingEnclave::class.java, block = Consumer { enclaveHandle ->
            val sender = enclaveHandle.connection.addDownstream(handler)
            sender.send("")
            assertEquals(1, handler.calls.size)
            assertEquals(EnclaveNameReadingEnclave::class.java.name, handler.calls[0])
        })
    }

    class RepeatedOcallEnclave : StringEnclave() {
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            for (i in 1 .. 100) {
                sender.send("Hello $i")
            }
        }
    }

    @Test
    fun severalOcallsWork() {
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), RepeatedOcallEnclave::class.java, block = Consumer { enclaveHandle ->
            val sender = enclaveHandle.connection.addDownstream(handler)
            sender.send("")
            val resultStrings = handler.calls
            assertEquals(100, resultStrings.size)
            val expectedStrings = (1..100).map { "Hello $it" }
            assertEquals(expectedStrings, resultStrings)
        })
    }

    class AddingEnclave : StringEnclave() {
        var N: Int? = null
        val sum = AtomicInteger(0)
        val numberOfCalls = AtomicInteger(0)
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            if (N == null) {
                N = Integer.parseInt(string)
                return
            }
            val number = Integer.parseInt(string)
            val sum = sum.addAndGet(number)
            val numberOfCalls = numberOfCalls.incrementAndGet()
            if (numberOfCalls == N) {
                sender.send(sum.toString())
            }
        }
    }

    @Test
    fun parallelEcallsWork() {
        val N = 10000L
        val numbers = (1 .. N).toList()
        val handler = StringRecordingHandler()
        val builder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(20))
        withEnclaveHandle(RootHandler(), AddingEnclave::class.java, builder, Consumer { enclaveHandle ->
            val sender = enclaveHandle.connection.addDownstream(handler)
            sender.send(N.toString())
            numbers.parallelStream().forEach {
                sender.send(it.toString())
            }
            assertEquals(1, handler.calls.size)
            assertEquals(((N * (N + 1)) / 2).toInt(), Integer.parseInt(handler.calls[0]))
        })
    }

    class IncrementingEnclave: StringEnclave() {
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            val N = Integer.parseInt(string)
            sender.send((N + 1).toString())
        }
    }

    @Test
    fun tcsAllocationPolicy() {
        val tcs = 20
        val builder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(tcs))
        val lock = ReentrantLock()
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), IncrementingEnclave::class.java, builder, Consumer { enclaveHandle ->
            val ecall = enclaveHandle.connection.addDownstream(handler)

            // Check that TCS are NOT by default bound to application threads
            val concurrentCalls = tcs + 20
            val ongoing = CountDownLatch(concurrentCalls)
            val threads = (1..concurrentCalls).map {
                thread {
                    lock.withLock {
                        ecall.send(it.toString())
                        assertEquals(Integer.parseInt(handler.calls.last()), it + 1)
                    }
                    ongoing.countDown()
                    ongoing.await()
                }
            }

            threads.forEach { it.join() }
        })
    }

    class WaitingEnclave: StringEnclave() {

        companion object {
            const val PAR_ECALLS = 16
        }

        val ecalls = AtomicInteger(0)
        val ocalls = AtomicInteger(0)

        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            ecalls.incrementAndGet()
            while (ecalls.get() < PAR_ECALLS) {
            }
            synchronized(this) {
                sender.send(string)
            }
            if (ocalls.incrementAndGet() == PAR_ECALLS) {
                ecalls.set(0)
                ocalls.set(0)
            }
        }
    }

    @Test
    fun tcsReallocation() {
        val tcs = WaitingEnclave.PAR_ECALLS + 3 // Some TCS are reserved for Avian internal threads
        val builder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(tcs))
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), WaitingEnclave::class.java, builder, Consumer { enclaveHandle ->
            val ecall = enclaveHandle.connection.addDownstream(handler)
            for (j in 1..3) {
                val threads = (1..WaitingEnclave.PAR_ECALLS).map {
                    thread { ecall.send(it.toString()) }
                }
                threads.forEach { it.join() }
                assertEquals(threads.size, handler.calls.size)
                handler.calls.clear()
            }
        })
    }

    class ThreadingEnclave : StringEnclave() {
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            val N = Integer.valueOf(string)
            val semaphore = Semaphore(0)
            val latchBefore = CountDownLatch(N)
            val latchAfter = CountDownLatch(N)
            val sum = AtomicInteger(0)
            try {
                for (i in 1..N) {
                    thread {
                        latchBefore.countDown()
                        semaphore.acquire()
                        sum.addAndGet(i)
                        latchAfter.countDown()
                    }
                }
                latchBefore.await() // wait until all threads are started
                semaphore.release(N) // unblock threads
                latchAfter.await() // wait until all threads finished adding
                sender.send(sum.get().toString())
            } finally {
                semaphore.release(N) // in case an exception occurred before releasing the semaphore
            }
        }
    }

    @Test
    fun threadingWorks() {
        val N = 15L
        val handler = StringRecordingHandler()
        val builder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(20))
        withEnclaveHandle(RootHandler(), ThreadingEnclave::class.java, builder, Consumer { enclaveHandle ->
            val sender = enclaveHandle.connection.addDownstream(handler)
            sender.send(N.toString())
            assertEquals(1, handler.calls.size)
            assertEquals(((N * (N + 1)) / 2).toInt(), Integer.parseInt(handler.calls[0]))
        })
    }

    @Test
    fun exceptionIsThrownIfTooManyThreadsAreRequested() {
        exception.expectMessage("SGX_ERROR_OUT_OF_TCS")
        val N = 15
        val handler = StringRecordingHandler()
        val builder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(10))
        // Note: enclaveHandle.destroy hangs due to inconsistent internal Avian thread state after SGX_ERROR_OUT_OF_TCS,
        // so we cant properly shutdown in this case
        val enclaveHandle = createEnclaveWithHandler(RootHandler(), ThreadingEnclave::class.java, builder)
        val sender = enclaveHandle.connection.addDownstream(handler)
        sender.send(N.toString())
        assertEquals(1, handler.calls.size)
        assertEquals((N.toLong() * (N + 1) / 2).toInt(), Integer.parseInt(handler.calls[0]))
    }

    @Test
    fun testJniMemoryLeaks() {
        val enclaveOcalls = object : BytesHandler() {
            override fun onReceive(connection: BytesHandler.Connection, input: ByteBuffer) {
            }
        }
        withEnclaveHandle(RootHandler(), EchoEnclave::class.java, block = Consumer { enclaveHandle ->
            val connection = enclaveHandle.connection.addDownstream(enclaveOcalls)
            val message = ByteArray(500000) { (it % 256).toByte() }
            for (n in 1..1000) {
                connection.send(ByteBuffer.wrap(message))
            }
        })
    }

    @Test
    fun destroyWhileOcallInProgress() {
        val handle = createEnclaveWithHandler(RootHandler(), EchoEnclave::class.java)
        val semaphore = CompletableFuture<Unit>()
        val handler = object: BytesHandler() {
            var ocalls  = AtomicInteger(0)
            override fun onReceive(connection: Connection, input: ByteBuffer) {
                val ocalls_value = ocalls.getAndIncrement()
                if (ocalls_value == 0) {
                    semaphore.get()
                    connection.send(input)
                }
            }
        }
        val msg = ByteBuffer.wrap(ByteArray(16) {0})
        val sender = handle.connection.addDownstream(handler)
        val ecall = thread {
            sender.send(msg)
        }
        while (handler.ocalls.get() == 0) {
            Thread.sleep(100)
        }
        val destructor = thread {
            handle.destroy()
        }
        semaphore.complete(Unit)
        destructor.join()
        ecall.join()
        assertEquals(2, handler.ocalls.get())
    }

    class SpinningEnclave : StringEnclave() {
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            sender.send("")
            while (true) {
            }
        }
    }

    @Ignore("This test demonstrates the waiting behaviour of enclave destruction")
    @Test
    fun destroyWhileEcallInProgress() {
        val handler = StringRecordingHandler()
        val handle = createEnclaveWithHandler(RootHandler(), SpinningEnclave::class.java)
        val sender = handle.connection.addDownstream(handler)
        thread(isDaemon = true) {
            sender.send("")
        }
        while (handler.calls.isEmpty()) {
            // Wait until the enclave signals the ECALL is in progress
            Thread.sleep(1)
        }
        handle.destroy() // hang
    }

    @Test
    fun destroyInOcall() {
        val handle = createEnclaveWithHandler(RootHandler(), EchoEnclave::class.java)
        var called = false
        val sender = handle.connection.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                called = true
                handle.destroy()
            }
        })
        assertFails("enclave destruction not supported inside nested calls") {
            sender.send("echo")
        }
        assertTrue(called, "Ocall must be called")
    }

    class ChildThreadSendingEnclave : StringEnclave() {
        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            thread {
                sender.send("test")
            }.join()
        }
    }

    @Test
    fun childThreadCanDoOcalls() {
        val builder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(10))
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), ChildThreadSendingEnclave::class.java, builder, Consumer { enclaveHandle ->
            val sender = enclaveHandle.connection.addDownstream(handler)
            sender.send("")
            assertEquals(1, handler.calls.size)
            assertEquals("test", handler.calls.first())
        })
    }

    class ThrowingEnclave : StringEnclave() {
        companion object {
            val CHEERS = "You are all wrong"
        }

        override fun onReceive(api: EnclaveApi, sender: StringSender, string: String) {
            throw RuntimeException(CHEERS)
        }
    }

    @Test
    fun throwingInEcallWorks() {
        exception.expectMessage(ThrowingEnclave.CHEERS)
        withEnclaveHandle(RootHandler(), ThrowingEnclave::class.java, block = Consumer { enclaveHandle ->
            val connection = enclaveHandle.connection.addDownstream(StringRecordingHandler())
            connection.send("ping")
        })
    }
}

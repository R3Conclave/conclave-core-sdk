package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.enclave.internal.SocketEnclaveHostInterface
import com.r3.conclave.utilities.internal.getRemainingString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

/**
 * Test the socket call interface classes.
 * It should be noted that the use of [EnclaveCallType] and [HostCallType] in these tests isn't important.
 * There is no enclave or host present for any of these tests and the enums are re-used solely for convenience.
 */
class SocketCallInterfaceTest {
    companion object {
        private const val SOCKET_PORT_NUMBER = 31893
        private const val ENCLAVE_HOST_INTERFACE_THREADS = 8
    }

    private lateinit var hostEnclaveInterface: SocketHostEnclaveInterface
    private lateinit var enclaveHostInterface: SocketEnclaveHostInterface

    @BeforeEach
    fun startInterfaces() {
        hostEnclaveInterface = SocketHostEnclaveInterface(SOCKET_PORT_NUMBER)
        enclaveHostInterface = SocketEnclaveHostInterface("127.0.0.1", SOCKET_PORT_NUMBER, ENCLAVE_HOST_INTERFACE_THREADS)

        val eThread = Thread {
            for (i in 0 until 10) {
                try {
                    enclaveHostInterface.start()
                    break
                } catch (error: IOException) {
                    Thread.sleep(10)
                }
            }
        }.apply { start() }

        val hThread = Thread {
            hostEnclaveInterface.start()
        }.apply { start() }

        eThread.join()
        hThread.join()
    }

    /** Stop both interfaces */
    @AfterEach
    fun stopInterfaces() {
        val enclaveStopThread = Thread {
            enclaveHostInterface.close()
        }.apply { start() }

        hostEnclaveInterface.close()

        enclaveStopThread.join()
    }

    /** Configure the enclave call interface for basic calls with an arbitrary task */
    private fun configureEnclaveCallAction(action: (ByteBuffer) -> ByteBuffer?) {
        enclaveHostInterface.registerCallHandler(EnclaveCallType.CALL_MESSAGE_HANDLER, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                return action(parameterBuffer)
            }
        })
    }

    /** Configure the host call interface for basic calls with an arbitrary task */
    private fun configureHostCallAction(action: (ByteBuffer) -> ByteBuffer?) {
        hostEnclaveInterface.registerCallHandler(HostCallType.CALL_MESSAGE_HANDLER, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                return action(parameterBuffer)
            }
        })
    }

    /** This method sets up the call interfaces to perform a recursive fibonacci computation */
    private fun configureInterfacesForRecursiveFibonacci() {
        configureHostCallAction {
            when (val index = it.int) {
                0 -> 0
                1 -> 1
                else -> {
                    val a = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, (index - 1).toByteBuffer())!!.int
                    val b = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, (index - 2).toByteBuffer())!!.int
                    a + b
                }
            }.toByteBuffer()
        }

        configureEnclaveCallAction {
            when (val index = it.int) {
                0 -> 0
                1 -> 1
                else -> {
                    val a = enclaveHostInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER, (index - 1).toByteBuffer())!!.int
                    val b = enclaveHostInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER, (index - 2).toByteBuffer())!!.int
                    a + b
                }
            }.toByteBuffer()
        }
    }

    /**
     * Configure the call interfaces for deep recursion.
     * This setup will recurse some number of times until reaching zero, then perform the provided action.
     */
    private fun configureInterfacesForDeepRecursion(terminatingAction: () -> ByteBuffer?) {
        configureHostCallAction {
            when (val recursionDepth = it.int) {
                0 -> terminatingAction()
                else -> hostEnclaveInterface.executeOutgoingCall(
                        EnclaveCallType.CALL_MESSAGE_HANDLER,
                        (recursionDepth - 1).toByteBuffer())
            }
        }

        configureEnclaveCallAction {
            when (val recursionDepth = it.int) {
                0 -> terminatingAction()
                else -> enclaveHostInterface.executeOutgoingCall(
                        HostCallType.CALL_MESSAGE_HANDLER,
                        (recursionDepth - 1).toByteBuffer())
            }
        }
    }

    private fun referenceFibonacci(index: Int): Int {
        if (index < 2) return index
        val values = IntArray(index + 1)
        values[0] = 0
        values[1] = 1
        for (i in 2 .. index) {
            values[i] = values[i - 1] + values[i - 2]
        }
        return values[index]
    }

    private fun Int.toByteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        buffer.putInt(this)
        buffer.rewind()
        return buffer
    }

    @Test
    fun `cannot call a stopped host enclave interface`() {
        stopInterfaces()

        val exception = assertThrows<IllegalStateException> {
            hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER)
        }

        assertThat(exception).hasMessage("Call interface is not running.")
    }

    @Test
    fun `host enclave interface stop waits for executing calls to return`() {
        val waitSemaphore = Semaphore(0)
        val callInProgressSemaphore = Semaphore(0)

        configureEnclaveCallAction {
            callInProgressSemaphore.release()
            waitSemaphore.acquireUninterruptibly()
            null
        }

        val callThread = Thread {
            hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER)
        }.apply { start() }

        val stopThread = Thread {
            callInProgressSemaphore.acquireUninterruptibly()
            hostEnclaveInterface.close()
        }.apply { start() }

        assertThat(callThread.isAlive).isTrue
        assertThat(stopThread.isAlive).isTrue

        waitSemaphore.release() // Allow the enclave call to return

        callThread.join(1000)
        stopThread.join(1000)

        assertThat(stopThread.isAlive).isFalse
        assertThat(callThread.isAlive).isFalse
    }

    @Test
    fun `host interface can call enclave interface`() {
        // Just echo the buffer back to the host side
        configureEnclaveCallAction { it }

        val inputString = "Test string!"
        val inputBuffer = ByteBuffer.wrap(inputString.toByteArray())
        val outputString = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, inputBuffer)?.getRemainingString()

        assertThat(outputString).isEqualTo(inputString)
    }

    /**
     * In native mode, threads literally call the enclave from the host and return out of it. The enclave does not have
     * any threads of its own.
     * Because of this, the enclave cannot call the host without the host first calling the enclave. To simplify the
     * implementation of the stream call interfaces, they have been written such that this remains the case
     * (even though it need not necessarily be so).
     * This test checks to see if this behaviour is intact. This helps to ensure that there are as few
     * differences between the stream and native interfaces as possible.
     */
    @Test
    fun `enclave interface may not call host interface outside the context of an enclave call`() {
        val exception = assertThrows<IllegalStateException> {
            enclaveHostInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER)
        }
        assertThat(exception).hasMessage("Outgoing host calls may not occur outside the context of an enclave call.")
    }

    @Test
    fun `enclave interface can call host interface inside the context of an enclave call`() {
        // Enclave forwards call back to host
        configureEnclaveCallAction {
            enclaveHostInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER, it)
        }

        // Host handles call by echoing it
        configureHostCallAction { it }

        val inputString = "This is an input string!"
        val inputBuffer = ByteBuffer.wrap(inputString.toByteArray())
        val outputString = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, inputBuffer)?.getRemainingString()

        assertThat(outputString).isEqualTo(inputString)
    }

    /** For this test to pass, two threads must be in the enclave simultaneously */
    @Test
    fun `interfaces can service concurrent calls`() {
        val waitSemaphore = Semaphore(0)

        // Allow us to acquire and release the semaphore in multiple threads
        configureEnclaveCallAction {
            when (it.int) {
                0 -> waitSemaphore.acquireUninterruptibly()
                else -> waitSemaphore.release()
            }
            null
        }

        val acquireThread = Thread {
            hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, 0.toByteBuffer())
        }.apply { start() }

        val releaseThread = Thread {
            hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, 1.toByteBuffer())
        }.apply { start() }

        acquireThread.join(1000)
        releaseThread.join(1000)

        assertThat(acquireThread.isAlive).isFalse
        assertThat(acquireThread.isAlive).isFalse
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 4, 8, 16, 32])
    fun `interfaces can perform deeply recursive calls`(recursionDepth: Int) {
        configureInterfacesForDeepRecursion { null }    // Don't return anything at the tail of the recursive call
        val returnValue = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, recursionDepth.toByteBuffer())
        assertThat(returnValue).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 4, 8, 16, 32])
    fun `exceptions propagate recursively across interfaces`(recursionDepth: Int) {
        val exceptionMessage = "End of the line!"
        configureInterfacesForDeepRecursion { throw IllegalStateException(exceptionMessage) }

        val exception = assertThrows<IllegalStateException> {
            hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, recursionDepth.toByteBuffer())
        }

        assertThat(exception).hasMessage(exceptionMessage)
    }

    /**
     * This test implements a trivial recursive fibonacci sequence.
     * This tests recursion and cases where enclave/host calls contain multiple other enclave/host calls.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4, 5, 6, 7])
    fun `interfaces can perform deeply recursive branching calls`(fibonacciIndex: Int) {
        configureInterfacesForRecursiveFibonacci()

        val fibonacciResult = hostEnclaveInterface.executeOutgoingCall(
                EnclaveCallType.CALL_MESSAGE_HANDLER, fibonacciIndex.toByteBuffer())!!.int

        assertThat(fibonacciResult).isEqualTo(referenceFibonacci(fibonacciIndex))
    }

    /**
     * This test implements a large number of concurrent calls.
     * This tests that the synchronisation mechanisms deal properly with contention on the host side and that return
     * values are sent back to the right caller when multiple callers are present.
     */
    @ParameterizedTest
    @ValueSource(ints = [2, 8, 256])
    fun `interface can service many concurrent calls`(concurrency: Int) {
        // Sleep for the specified duration and send the duration back to the caller
        configureEnclaveCallAction {
            val sleepFor = it.int
            Thread.sleep(sleepFor.toLong())
            sleepFor.toByteBuffer()
        }

        class SlowCountRunner(val input: Int) : Runnable {
            var finalCount: Int? = null
            var error: Throwable? = null

            override fun run() {
                try {
                    finalCount = checkNotNull(hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, input.toByteBuffer())) {
                        "No return value was received!"
                    }.int
                } catch (t: Throwable) {
                    error = t
                }
            }
        }

        val slowCountRunners = ArrayList<SlowCountRunner>(concurrency).apply {
            for (i in 0 until concurrency) {
                add(SlowCountRunner((32..64).random()))
            }
        }

        val threads = slowCountRunners.map { Thread(it) }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Double check that calls were not mixed up
        slowCountRunners.forEach {
            assertThat(it.finalCount).isEqualTo(it.input)
        }
    }

    /**
     * This is a hammer test that implements a large number of concurrent recursive fibonacci calls.
     * The purpose of this test is to stress the message delivery and synchronisation mechanisms by causing large
     * numbers of messages to be delivered and received in multiple threads.
     */
    @ParameterizedTest
    @ValueSource(ints = [2, 8, 256])
    fun `interface can service many concurrent recursive calls`(concurrency: Int) {
        configureInterfacesForRecursiveFibonacci()

        class RecursiveFibonacciRunner(val input: Int) : Runnable {
            var result: Int? = null
            var error: Throwable? = null

            override fun run() {
                try {
                    result = checkNotNull(hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, input.toByteBuffer())) {
                        "No return value was received!"
                    }.int
                } catch (t: Throwable) {
                    error = t
                }
            }
        }

        val fibonacciRunners = ArrayList<RecursiveFibonacciRunner>(concurrency).apply {
            for (i in 0 until concurrency) {
                add(RecursiveFibonacciRunner((8..12).random()))
            }
        }

        val threads = fibonacciRunners.map { Thread(it) }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Check for correct values
        fibonacciRunners.forEach {
            assertThat(it.result).isEqualTo(referenceFibonacci(it.input))
        }
    }

    /**
     * The stream call interface uses a thread pool on the enclave side.
     * We sometimes rely on the thread ID for logic, so we need this to be consistent when re-entering the enclave.
     */
    @Test
    fun `thread IDs are consistent when calls are re-entered`() {
        val hostSideThreadIDs = ArrayList<Long>()
        val enclaveSideThreadIDs = ArrayList<Long>()

        configureHostCallAction {
            hostSideThreadIDs.add(Thread.currentThread().id)
            when (val recursionDepth = it.int) {
                0 -> null
                else -> hostEnclaveInterface.executeOutgoingCall(
                        EnclaveCallType.CALL_MESSAGE_HANDLER, (recursionDepth - 1).toByteBuffer())
            }
        }

        configureEnclaveCallAction {
            enclaveSideThreadIDs.add(Thread.currentThread().id)
            when (val recursionDepth = it.int) {
                0 -> null
                else -> enclaveHostInterface.executeOutgoingCall(
                        HostCallType.CALL_MESSAGE_HANDLER, (recursionDepth - 1).toByteBuffer())
            }
        }

        hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, 16.toByteBuffer())

        // Check that all host side and all enclave side thread IDs are identical.
        assertThat(setOf(hostSideThreadIDs)).hasSize(1)
        assertThat(setOf(enclaveSideThreadIDs)).hasSize(1)
    }
}

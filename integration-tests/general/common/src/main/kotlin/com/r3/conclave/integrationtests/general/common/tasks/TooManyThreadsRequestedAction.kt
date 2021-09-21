package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@Serializable
class TooManyThreadsRequestedAction(private val n: Int) : EnclaveTestAction<Int>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Int {
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

            return sum.get()
        } finally {
            semaphore.release(n) // in case an exception occurred before releasing the semaphore
        }
    }

    override fun resultSerializer(): KSerializer<Int> = Int.serializer()
}

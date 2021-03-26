package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@Serializable
class Sum1ToN(val n: Int) : JvmTestTask(), Deserializer<Int> {

    override fun run(context: RuntimeContext): ByteArray {
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

            return Json.encodeToString(Int.serializer(), sum.get()).toByteArray()
        } finally {
            semaphore.release(n) // in case an exception occurred before releasing the semaphore
        }
        return Json.encodeToString(Int.serializer(), -1).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Int {
        return decode(encoded)
    }
}
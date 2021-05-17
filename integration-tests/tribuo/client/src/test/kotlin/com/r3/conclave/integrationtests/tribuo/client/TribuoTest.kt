package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.host.Host
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@EnabledForJreRange(min = JRE.JAVA_8, max = JRE.JAVA_8, disabledReason = "File system support is only available for Java 8")
open class TribuoTest {
    companion object {
        private val initialized = AtomicBoolean(false)
        private val hostFailed = AtomicBoolean(false)
        lateinit var client: Client
        private lateinit var host: Thread

        @BeforeAll
        @JvmStatic
        fun setup() {
            if (initialized.compareAndSet(false, true)) {
                host = thread {
                    Host.main(emptyArray())
                }.also {
                    it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                        e?.printStackTrace()
                        hostFailed.set(true)
                    }
                }
                while (!hostFailed.get()) {
                    try {
                        client = Client()
                        break
                    } catch (e: Exception) {
                        println("Retrying: " + e.message)
                        Thread.sleep(2000)
                    }
                }
                if (hostFailed.get()) {
                    throw RuntimeException("Host failed to start.")
                }
            }
        }
    }
}
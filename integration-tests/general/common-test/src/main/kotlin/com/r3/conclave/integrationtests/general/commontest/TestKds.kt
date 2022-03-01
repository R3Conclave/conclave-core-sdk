package com.r3.conclave.integrationtests.general.commontest

import java.net.ServerSocket
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

object TestKds {
    val testKdsPort = startKds()

    private fun startKds(): Int {
        val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
        val kdsJar = checkNotNull(System.getProperty("kdsJar"))
        val randomPort = ServerSocket(0).use { it.localPort }
        val kdsCmd = listOf(java, "-jar", kdsJar, "--server.port=$randomPort")
        println("Starting KDS: $kdsCmd")
        val process = ProcessBuilder(kdsCmd).redirectErrorStream(true).start()

        // Kill the KDS sub-process when the test worker process is done.
        Runtime.getRuntime().addShutdownHook(Thread(process::destroyForcibly))

        // Start a background thread that prints the KDS's log output to help with debugging and signals when it's
        // ready to accept requests.
        val kdsReadySignal = CountDownLatch(1)
        thread(isDaemon = true) {
            val kdsOutput = process.inputStream.bufferedReader()
            while (true) {
                val line = kdsOutput.readLine()
                if (line == null) {
                    println("KDS EOF")
                    break
                }
                println("KDS> $line")
                if ("Started KdsApplication.Companion in " in line) {
                    kdsReadySignal.countDown()
                }
            }
        }

        println("Waiting for KDS to be ready...")
        kdsReadySignal.await()
        println("KDS ready")
        return randomPort
    }
}
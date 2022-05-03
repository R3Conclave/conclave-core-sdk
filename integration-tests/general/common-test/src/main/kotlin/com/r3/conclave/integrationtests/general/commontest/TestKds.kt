package com.r3.conclave.integrationtests.general.commontest

import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

object TestKds {

    val testKdsPort = startKds()

    // Use JDK 17 inside the build container to start the KDS. The SDK build in this release does not use Java
    // 17 so we cannot use the java.home system property.
    private const val javaPath = "/usr/lib/jvm/java-17-openjdk-amd64/bin/java"

    private fun startKds(): Int {
        val randomPort = ServerSocket(0).use { it.localPort }
        val fileSystemTempFile = Files.createTempFile("kds_filesystem", ".disk")
        val kdsMasterKeyCmd = getMasterKeyClusterCommand(fileSystemTempFile)
        val kdsServiceCmd = getServiceCommand(fileSystemTempFile, randomPort)

        println("Generating KDS Master Key: $kdsMasterKeyCmd")

        val processMasterKey = ProcessBuilder(kdsMasterKeyCmd).redirectErrorStream(true).start()
        processMasterKey.waitFor()
        check(processMasterKey.exitValue() == 0) { "Could not generate the master key for the KDS" }

        println("Starting KDS: $kdsServiceCmd")
        val process = ProcessBuilder(kdsServiceCmd).redirectErrorStream(true).start()

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

    private fun getServiceCommand(fileSystemTempFile: Path, randomPort: Int): List<String> {
        val kdsCmd = getCommonCommand(fileSystemTempFile)
        return kdsCmd + listOf("--server-port=$randomPort", "--service")
    }

    private fun getMasterKeyClusterCommand(fileSystemTempFile: Path): List<String> {
        val kdsCmd = getCommonCommand(fileSystemTempFile)
        return kdsCmd + listOf("--generate-cluster-master-key")
    }

    private fun getCommonCommand(fileSystemTempFile: Path): List<String> {
        val kdsJar = checkNotNull(System.getProperty("kdsJar"))

        return listOf(
            javaPath,
            "-jar",
            kdsJar,
            "--filesystem-file=$fileSystemTempFile"
        )
    }
}

package com.r3.conclave.enclave.internal

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue

class GramineEntryPoint {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Socket("127.0.0.1", args[0].toInt()).use {
                it.tcpNoDelay = true

                val fromHost = DataInputStream(it.getInputStream())
                val toHost = DataOutputStream(it.getOutputStream())

                val queue = ArrayBlockingQueue<Int>(2)

                val workerThread = Thread {
                    do {
                        val intFromQueue = queue.take()
                        println("[worker] got the value $intFromQueue from the queue.")
                        if (intFromQueue >= 0) {
                            synchronized(toHost) {
                                println("[worker] sending incremented value back to host.")
                                toHost.writeInt(intFromQueue + 1)
                                toHost.flush()
                                println("[worker] incremented value sent.")
                            }
                        }
                    } while (intFromQueue >= 0)
                }.apply { start() }

                do {
                    val intFromHost = fromHost.readInt()
                    println("[main] received $intFromHost from the host.")
                    queue.put(intFromHost)
                    println("[main] added $intFromHost to the queue.")
                } while (intFromHost >= 0)

                workerThread.join()
            }

            Thread.sleep(10000)
        }
    }
}

package com.r3.conclave.internaltesting

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.net.ServerSocket
import java.net.URL

internal class EmbeddedServer {
    val host = "0.0.0.0"
    // Use the port number that is automatically allocated
    val port = ServerSocket(0).use { it.localPort }
    val hostUrl = URL("http://${host}:${port}")

    private val server = createServer()

    fun installRoutes(configure: Routing.() -> Unit = {}) {
        server.application.install(Routing, configure)
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(0, 0)
    }

    private fun createServer(): NettyApplicationEngine {
        return embeddedServer(Netty, host = host, port = port) {
            install(ContentNegotiation) {
                json()
            }
        }
    }
}
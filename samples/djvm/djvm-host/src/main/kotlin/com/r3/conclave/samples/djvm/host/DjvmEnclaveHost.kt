package com.r3.conclave.samples.djvm.host

import com.google.protobuf.ByteString
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.samples.djvm.common.proto.ClearJars
import com.r3.conclave.samples.djvm.common.proto.Request
import com.r3.conclave.samples.djvm.common.proto.TaskResult
import java.nio.file.Files
import java.nio.file.Path

class DjvmEnclaveHost : AutoCloseable {
    private val enclaveHost = EnclaveHost.load("com.r3.conclave.samples.djvm.enclave.DjvmEnclave")

    fun start(spid: OpaqueBytes?, attestationKey: String?) {
        enclaveHost.start(spid, attestationKey)
    }

    fun loadJarIntoEnclave(jarFile: Path) {
        callEnclave {
            sendJarBuilder.data = ByteString.copyFrom(Files.readAllBytes(jarFile))
        }
    }

    fun runTaskInEnclave(className: String, input: String): String? {
        val response = callEnclave {
            executeTaskBuilder.className = className
            executeTaskBuilder.input = input
        }
        return TaskResult.parseFrom(response!!).let { if (it.hasResult()) it.result else null }
    }

    private inline fun callEnclave(block: Request.Builder.() -> Unit): ByteArray? {
        val request = Request.newBuilder()
        block(request)
        return enclaveHost.callEnclave(request.build().toByteArray())
    }

    override fun close() {
        callEnclave { clearJars = ClearJars.getDefaultInstance() }
        // destroy can trigger an assertion failure in Avian
//        enclaveHost.close()
    }
}

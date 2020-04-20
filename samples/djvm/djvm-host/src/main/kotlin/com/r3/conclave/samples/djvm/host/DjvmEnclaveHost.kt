package com.r3.conclave.samples.djvm.host

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.samples.djvm.common.MessageType
import com.r3.conclave.samples.djvm.common.Status
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

class DjvmEnclaveHost : AutoCloseable {
    private val enclaveHost = EnclaveHost.load("com.r3.conclave.samples.djvm.enclave.DjvmEnclave")

    fun start(spid: OpaqueBytes?, attestationKey: String?) {
        enclaveHost.start(spid, attestationKey)
    }

    fun loadJarIntoEnclave(jarFile: Path) {
        val jarBytes = Files.readAllBytes(jarFile)
        val message = ByteBuffer
                .allocate(Int.SIZE_BYTES + jarBytes.size)
                .putInt(MessageType.JAR.ordinal)
                .put(jarBytes)
                .array()
        val response = checkNotNull(enclaveHost.callEnclave(message)) { "Was expecting a response from the enclave" }
        val reply = ByteBuffer.wrap(response).getInt()
        check(reply == Status.OK.ordinal) { "Unable to load Jar into enclave!" }
    }

    fun runTaskInEnclave(className: String, input: String): String {
        val classNameBytes = className.toByteArray()
        val inputBytes = input.toByteArray()
        val message = ByteBuffer
                .allocate(Int.SIZE_BYTES + Int.SIZE_BYTES + classNameBytes.size + inputBytes.size)
                .putInt(MessageType.TASK.ordinal)
                .putInt(classNameBytes.size)
                .put(classNameBytes)
                .put(inputBytes)  //  We don't need to send the inputBytes size as the enclave can use the remaining bytes
                .array()
        val response = checkNotNull(enclaveHost.callEnclave(message)) { "Was expecting a response from the enclave" }
        return String(response)
    }

    override fun close() {
        // An empty JAR request does a clear of the enclave state
        enclaveHost.callEnclave(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(MessageType.JAR.ordinal).array())
        // destroy can trigger an assertion failure in Avian
//        enclaveHost.close()
    }
}

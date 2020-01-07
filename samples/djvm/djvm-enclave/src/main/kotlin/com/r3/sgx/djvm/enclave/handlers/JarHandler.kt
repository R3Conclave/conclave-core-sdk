package com.r3.sgx.djvm.enclave.handlers

import com.r3.sgx.djvm.enclave.DJVMMemoryURLStreamHandler
import com.r3.sgx.djvm.enclave.connections.DJVMConnection
import com.r3.sgx.djvm.enclave.messages.MessageType
import com.r3.sgx.djvm.enclave.messages.Status
import com.r3.sgx.utils.classloaders.MemoryURL
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.function.Consumer

/**
 * @userJars Jars containing the user code will be added to this list to be setup and loaded in the DJVM by [TaskHandler]
 */
class JarHandler {
    companion object {
        fun createMemoryURL(input: ByteBuffer): MemoryURL {
            return DJVMMemoryURLStreamHandler.createURL(input.sha256.hashKey, input)
        }
    }

    /**
     * Receiving a message with no data clears the enclaves' in memory file system.
     */
    fun onReceive(connection: DJVMConnection, input: ByteBuffer) {
        try {
            val jarSizeBytes = input.int
            if (jarSizeBytes > 0) {
                val byteArray = ByteArray(jarSizeBytes)
                input.get(byteArray)
                connection.userJars.add(createMemoryURL(ByteBuffer.wrap(byteArray)))

                reply(connection, Status.OK)
            } else {
                DJVMMemoryURLStreamHandler.clear()
            }
        } catch (throwable: Throwable) {
            reply(connection, Status.FAIL)
        }
    }

    private fun reply(connection: DJVMConnection, status: Status) {
        connection.send(2 * Int.SIZE_BYTES, Consumer { buffer ->
            buffer.putInt(MessageType.JAR.ordinal)
            buffer.putInt(status.ordinal)
        })
    }
}

const val SHA256_BYTES = 32

val ByteBuffer.sha256: ByteArray get() {
    return MessageDigest.getInstance("SHA-256").let { hash ->
        hash.update(slice())
        hash.digest()
    }
}

val ByteArray.hashKey: String get() {
    // Our 256 bit hash consists of 64 nybbles.
    return BigInteger(1, this)
            .toString(16).toLowerCase().padStart(2 * SHA256_BYTES, '0')
}

package com.r3.conclave.host.internal

import com.r3.conclave.host.internal.kds.KDSPrivateKeyResponse
import com.r3.conclave.utilities.internal.intLengthPrefixSize
import com.r3.conclave.utilities.internal.putIntLengthPrefixBytes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

inline fun <reified T> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) {
        debug(message())
    }
}

object UtilsOS {
    fun isLinux(): Boolean {
        val operatingSystemName: String = System.getProperty("os.name").lowercase()
        return operatingSystemName.contains("nix") || operatingSystemName.contains("nux") || operatingSystemName.contains("aix")
    }
}

val KDSPrivateKeyResponse.size: Int get() {
    return encryptedPrivateKey.intLengthPrefixSize + kdsAttestationReport.intLengthPrefixSize
}

fun ByteBuffer.putKdsPrivateKeyResponse(response: KDSPrivateKeyResponse) {
    putIntLengthPrefixBytes(response.encryptedPrivateKey)
    putIntLengthPrefixBytes(response.kdsAttestationReport)
}

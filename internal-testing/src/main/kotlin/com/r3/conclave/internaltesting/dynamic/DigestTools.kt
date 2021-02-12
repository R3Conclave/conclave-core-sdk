package com.r3.conclave.internaltesting.dynamic

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.experimental.and

object DigestTools {
    val sha256Digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    val hexArray = "0123456789ABCDEF".toCharArray()

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = (bytes[j] and 0xFF.toByte()).toInt() + 128

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun sha256String(input: String): String {
        return sha256ByteArray(input.toByteArray())
    }

    fun sha256InputStream(inputStream: InputStream): String {
        val uselessBuffer = ByteArray(64 * 1024)
        val digest = DigestInputStream(inputStream, sha256Digest).use { digestStream ->
            while (digestStream.read(uselessBuffer) > -1) {
            }
            digestStream.messageDigest
        }
        return bytesToHex(digest.digest())
    }

    fun sha256File(file: File): String {
        return sha256InputStream(FileInputStream(file))
    }

    fun sha256ByteArray(input: ByteArray): String {
        return bytesToHex(sha256Digest.digest(input))
    }
}
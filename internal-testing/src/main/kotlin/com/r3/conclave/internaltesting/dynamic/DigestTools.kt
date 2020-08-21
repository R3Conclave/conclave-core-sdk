package com.r3.conclave.internaltesting.dynamic

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.experimental.and

object DigestTools {
    val md5Digest: MessageDigest = MessageDigest.getInstance("MD5")

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

    fun md5String(input: String): String {
        return md5ByteArray(input.toByteArray())
    }

    fun md5InputStream(inputStream: InputStream): String {
        val uselessBuffer = ByteArray(64 * 1024)
        val digest = DigestInputStream(inputStream, md5Digest).use { digestStream ->
            while (digestStream.read(uselessBuffer) > -1) {}
            digestStream.messageDigest
        }
        return bytesToHex(digest.digest())
    }

    fun md5File(file: File): String {
        return md5InputStream(FileInputStream(file))
    }

    fun md5ByteArray(input: ByteArray): String {
        return bytesToHex(md5Digest.digest(input))
    }
}
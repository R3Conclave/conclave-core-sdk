package com.r3.conclave.plugin.enclave.gradle.util

import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

class ChecksumUtils {
    companion object {
        private const val BUFFER_SIZE = 8192

        @JvmStatic
        fun sha512(filePath: String): ByteArray {
            val messageDigest = MessageDigest.getInstance("SHA-512")
            DigestInputStream(FileInputStream(filePath), messageDigest).use {
                val buf = ByteArray(BUFFER_SIZE)
                while (it.available() > 0) {
                    it.read(buf)
                }
            }
            return messageDigest.digest()
        }

        @JvmStatic
        fun sha512Directory(path: String): List<ByteArray> {
            val checksums = mutableListOf<ByteArray>()
            val root = File(path)
            val iterator = root.walkTopDown().iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (!file.isDirectory) {
                    checksums.add(sha512(file.absolutePath))
                }
            }
            return checksums.toList()
        }
    }
}
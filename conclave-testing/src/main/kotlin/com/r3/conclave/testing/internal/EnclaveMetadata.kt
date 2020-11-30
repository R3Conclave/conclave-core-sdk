package com.r3.conclave.testing.internal

import com.r3.conclave.common.SHA256Hash
import java.nio.file.Files
import java.nio.file.Path

data class EnclaveMetadata(val mrenclave: SHA256Hash, val mrsigner: SHA256Hash) {
    companion object {
        fun parseMetadataFile(file: Path): EnclaveMetadata {
            val lines = Files.readAllLines(file)
            val mrenclave = parseSha256Value(lines, "metadata->enclave_css.body.enclave_hash.m:")
            val mrsigner = parseSha256Value(lines, "mrsigner->value:")
            return EnclaveMetadata(mrenclave, mrsigner)
        }

        // Example:
        //
        // metadata->enclave_css.body.enclave_hash.m:
        // 0xca 0x5d 0xb9 0x8c 0xde 0x0d 0x87 0xe5 0x47 0x0b 0x16 0x89 0x79 0xa2 0xa2 0x63
        // 0xe3 0xc9 0x99 0x19 0x61 0x63 0xf3 0xb5 0xda 0x3e 0x46 0xa8 0xa4 0x97 0xad 0x0d
        private fun parseSha256Value(metadataFile: List<String>, key: String): SHA256Hash {
            return metadataFile.asSequence()
                    .dropWhile { line -> line != key }
                    .drop(1)
                    .take(2)
                    .flatMap { line -> line.splitToSequence(' ').map { it.removePrefix("0x") } }
                    .joinToString("")
                    .let(SHA256Hash::parse)
        }
    }
}

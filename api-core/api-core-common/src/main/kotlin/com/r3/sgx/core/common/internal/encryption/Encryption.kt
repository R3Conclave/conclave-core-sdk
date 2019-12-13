package com.r3.sgx.core.common.internal.encryption

import com.r3.sgx.core.common.Sender
import java.nio.ByteBuffer
import java.security.AlgorithmParameters


/**
 *  Top level interface of decrypting side of a secure communication channel
 */
interface Decryptor {

    val spec: AlgorithmParameters

    fun process(input: ByteBuffer): ByteArray
}

/**
 * Top level class denoting sender objects which encrypt messages
 * forwarded to an upstream
 */
interface Encryptor {

    val spec: AlgorithmParameters

    fun process(input: ByteBuffer, output: Sender)
}



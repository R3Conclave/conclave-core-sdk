package com.r3.conclave.integrationtests.general.common.tasks

import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.atomic.AtomicInteger

abstract class RuntimeContext {
    val atomicIntegerOne = AtomicInteger(0)
    val atomicIntegerTwo = AtomicInteger(0)
    private val keyedValues = mutableMapOf<String,Any?>()

    abstract fun callHost(bytes: ByteArray): ByteArray?

    abstract fun getSigner(): Signature
    abstract fun getPublicKey(): PublicKey

    fun setValue(key: String, value: Any?) {
        keyedValues[key] = value
    }

    fun getValue(key: String): Any? {
        return keyedValues[key]
    }

}
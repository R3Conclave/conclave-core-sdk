package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.nio.file.Path

fun ObjectFactory.stringProperty(): Property<String> = property(String::class.java)
fun ObjectFactory.intProperty(): Property<Int> = property(Int::class.java)
fun ObjectFactory.booleanProperty(): Property<Boolean> = property(Boolean::class.java)
inline fun <reified T> ObjectFactory.newInstance(vararg parameters: Any): T = newInstance(T::class.java, *parameters)

fun String.toSizeBytes(): Long {
    return when {
        endsWith("G", ignoreCase = true) -> dropLast(1).toLong() shl 30
        endsWith("M", ignoreCase = true) -> dropLast(1).toLong() shl 20
        endsWith("K", ignoreCase = true) -> dropLast(1).toLong() shl 10
        else -> toLong()
    }
}

operator fun Path.div(other: String): Path = resolve(other)

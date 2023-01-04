package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.model.ObjectFactory
import java.nio.file.Path

operator fun Path.div(other: String): Path = resolve(other)

inline fun <reified T> ObjectFactory.newInstance(vararg parameters: Any): T = newInstance(T::class.java, *parameters)

enum class RuntimeType {
    GRAMINE,
    GRAALVM;
}

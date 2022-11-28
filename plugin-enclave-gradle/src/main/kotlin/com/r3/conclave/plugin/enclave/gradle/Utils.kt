package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.model.ObjectFactory
import java.nio.file.Path

inline fun <reified T> ObjectFactory.newInstance(vararg parameters: Any): T = newInstance(T::class.java, *parameters)

operator fun Path.div(other: String): Path = resolve(other)

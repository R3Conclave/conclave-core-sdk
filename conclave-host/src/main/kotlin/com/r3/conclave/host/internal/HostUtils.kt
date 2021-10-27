package com.r3.conclave.host.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

inline fun <reified T> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) {
        debug(message())
    }
}

object UtilsOS {
    fun isLinux(): Boolean {
        val operatingSystemName: String = System.getProperty("os.name").toLowerCase()
        return operatingSystemName.contains("nix") || operatingSystemName.contains("nux") || operatingSystemName.contains("aix")
    }
}
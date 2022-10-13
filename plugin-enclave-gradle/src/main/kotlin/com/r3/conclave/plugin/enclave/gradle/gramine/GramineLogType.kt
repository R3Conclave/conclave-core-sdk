package com.r3.conclave.plugin.enclave.gradle.gramine

enum class GramineLogType(val type: String) {
    ERROR("error"),
    WARNING("warning"),
    DEBUG("debug"),
    TRACE("trace"),
    ALL("all")
}

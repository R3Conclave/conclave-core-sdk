@file:JvmName("Sgx")
package com.r3.sgx.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException

const val SGX_GROUP = "sgx"

abstract class SgxTask : DefaultTask() {
    init {
        group = SGX_GROUP
    }

    @Throws(Exception::class)
    abstract fun sgxAction()

    @Suppress("unused")
    @TaskAction
    fun run() {
        try {
            sgxAction()
        } catch (e: Exception) {
            throw (e as? RuntimeException) ?: TaskExecutionException(this, e)
        }
    }
}

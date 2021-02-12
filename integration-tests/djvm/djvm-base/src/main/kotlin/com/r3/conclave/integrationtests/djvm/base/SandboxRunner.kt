package com.r3.conclave.integrationtests.djvm.base

import net.corda.djvm.execution.SandboxExecutor
import net.corda.djvm.source.ClassSource

class SandboxRunner : DJVMBase() {
    fun run(className: String, input: Any?): Any? {
        var result: Any? = null
        sandbox(emptySet(), emptySet()) {
            val classSource = ClassSource.fromClassName(className)
            val output = SandboxExecutor<Any?, Any?>(configuration, false).run(classSource, input).result
            result = output
        }
        return result
    }
}

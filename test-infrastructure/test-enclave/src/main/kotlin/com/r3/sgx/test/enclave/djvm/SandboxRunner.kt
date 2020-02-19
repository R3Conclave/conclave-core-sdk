package com.r3.sgx.test.enclave.djvm

import com.r3.sgx.djvm.DJVMBase
import net.corda.djvm.execution.SandboxExecutor
import net.corda.djvm.source.ClassSource

class SandboxRunner : DJVMBase() {
    fun run(className: String, input: Any?): Any? {
        var result : Any? = null
        sandbox(emptySet(), emptySet()) {
            val classSource = ClassSource.fromClassName(className)
            val output = SandboxExecutor<Any?, Any?>(configuration, false).run(classSource, input).result
            result = output
        }
        return result
    }
}

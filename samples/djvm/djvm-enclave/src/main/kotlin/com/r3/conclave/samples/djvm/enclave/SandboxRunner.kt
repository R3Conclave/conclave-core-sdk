package com.r3.conclave.samples.djvm.enclave

import net.corda.djvm.source.ClassSource

class SandboxRunner : DJVMBase() {
    fun run(className: String, input: Any?): Any? {
        var result : Any? = null
        sandbox {
            val classSource = ClassSource.fromClassName(className)
            val output = TaskExecutor(configuration).run(classSource, input).result
            result = output
        }
        return result
    }
}

package com.r3.sgx.djvm.enclave.internal

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.execution.ExecutionSummary
import net.corda.djvm.execution.ExecutionSummaryWithResult
import net.corda.djvm.execution.IsolatedTask
import net.corda.djvm.execution.SandboxException
import net.corda.djvm.messages.Message
import net.corda.djvm.source.ClassSource
import java.util.function.Function

/**
 * This class is a modified version of [net.corda.djvm.execution.SandboxExecutor] without generic types
 */
class TaskExecutor(private val configuration: SandboxConfiguration) {
    @Throws(Exception::class)
    fun run(
            runnableClass: ClassSource,
            input: Any?
    ): ExecutionSummaryWithResult<Any?> {
        val result = IsolatedTask(runnableClass.qualifiedClassName, configuration).run<Any>(Function { classLoader ->
            // Create the user's task object inside the sandbox.
            val runnable = classLoader.loadClassForSandbox(runnableClass).newInstance()

            val taskFactory = classLoader.createTaskFactory()
            val task = taskFactory.apply(runnable)

            // Execute the task...
            @Suppress("UNCHECKED_CAST")
            task.apply(input)
        })
        when (val ex = result.exception) {
            null -> return ExecutionSummaryWithResult(result.output, result.costs)
            else -> throw SandboxException(
                    Message.getMessageFromException(ex),
                    result.identifier,
                    runnableClass,
                    ExecutionSummary(result.costs),
                    ex
            )
        }
    }
}

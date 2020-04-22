package com.r3.conclave.jvmtester.djvm.tests;

import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxException;
import net.corda.djvm.execution.SandboxExecutor;
import net.corda.djvm.execution.SandboxRuntimeException;
import net.corda.djvm.source.ClassSource;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface WithJava {

    @NotNull
    static <T,R> ExecutionSummaryWithResult<R> run(
            SandboxExecutor<T, R> executor, Class<? extends Function<T,R>> task, T input) {
        try {
            return executor.run(ClassSource.fromClassName(task.getName(), null), input);
        } catch (Exception e) {
            if (e instanceof SandboxException) {
                Throwable cause = e.getCause();
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw asRuntime(cause);
            } else {
                throw asRuntime(e);
            }
        }
    }

    @NotNull
    static RuntimeException asRuntime(Throwable t) {
        return (t instanceof RuntimeException) ? (RuntimeException) t : new SandboxRuntimeException(t.getMessage(), t);
    }
}

package com.r3.conclave.integrationtests.djvm.sandboxtests;

import net.corda.djvm.TypedTaskFactory;
import net.corda.djvm.execution.SandboxRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface WithJava {

    @NotNull
    static <T, R> R run(TypedTaskFactory taskFactory, Class<? extends Function<T,R>> taskClass, T input) {
        try {
            return taskFactory.create(taskClass).apply(input);
        } catch (Exception e) {
            throw asRuntime(e);
        }
    }

    @NotNull
    static RuntimeException asRuntime(Throwable t) {
        return (t instanceof RuntimeException) ? (RuntimeException) t : new SandboxRuntimeException(t.getMessage(), t);
    }
}

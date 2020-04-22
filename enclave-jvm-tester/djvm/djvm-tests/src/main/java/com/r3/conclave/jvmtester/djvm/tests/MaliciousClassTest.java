package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.SandboxExecutor;
import net.corda.djvm.rewiring.SandboxClassLoadingException;
import net.corda.djvm.rules.RuleViolationError;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class MaliciousClassTest {

    public static class TestImplementingToDJVMStringEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable ex = catchThrowableOfType(() -> WithJava.run(executor, EvilToString.class, ""), SandboxClassLoadingException.class);
                assertThat(ex)
                        .hasMessageContaining("Class is not allowed to implement toDJVMString()");
                output.set(ex.getMessage());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.MaliciousClassTest.TestImplementingToDJVMString().assertResult(testResult);
        }
    }

    public static class EvilToString implements Function<String, String> {
        @Override
        public String apply(String s) {
            return toString();
        }

        @SuppressWarnings("unused")
        public String toDJVMString() {
            throw new IllegalStateException("Victory is mine!");
        }
    }

    public static class TestImplementingFromDJVMEnclaveTest extends DJVMBase implements EnclaveJvmTest {
        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Object, Object> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable ex = catchThrowableOfType(() -> WithJava.run(executor, EvilFromDJVM.class, null), SandboxClassLoadingException.class);
                assertThat(ex)
                        .hasMessageContaining("Class is not allowed to implement fromDJVM()");
                output.set(ex.getMessage());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.MaliciousClassTest.TestImplementingFromDJVM().assertResult(testResult);
        }
    }

    public static class EvilFromDJVM implements Function<Object, Object> {
        @Override
        public Object apply(Object obj) {
            return this;
        }

        @SuppressWarnings("unused")
        protected Object fromDJVM() {
            throw new IllegalStateException("Victory is mine!");
        }
    }

    public static class TestPassingClassIntoSandboxIsForbiddenEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Class<?>, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable ex = catchThrowableOfType(() -> WithJava.run(executor, EvilClass.class, String.class), RuleViolationError.class);
                assertThat(ex)
                        .hasMessageContaining("Cannot sandbox class java.lang.String");
                output.set(ex.getMessage());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.MaliciousClassTest.TestPassingClassIntoSandboxIsForbidden().assertResult(testResult);
        }
    }

    public static class EvilClass implements Function<Class<?>, String> {
        @Override
        public String apply(Class<?> clazz) {
            return clazz.getName();
        }
    }

    public static class TestPassingConstructorIntoSandboxIsForbiddenEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            try {
                Constructor<?> constructor = getClass().getDeclaredConstructor();
                sandbox(ctx -> {
                    SandboxExecutor<Constructor<?>, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                    Throwable ex = catchThrowableOfType(() -> WithJava.run(executor, EvilConstructor.class, constructor), RuleViolationError.class);
                    assertThat(ex)
                            .hasMessageContaining("Cannot sandbox " + constructor);
                    output.set(ex.getMessage());
                    return null;
                });
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.MaliciousClassTest.TestPassingConstructorIntoSandboxIsForbidden().assertResult(testResult);
        }
    }

    public static class EvilConstructor implements Function<Constructor<?>, String> {
        @Override
        public String apply(Constructor<?> constructor) {
            return constructor.getName();
        }
    }

    public static class TestPassingClassLoaderIntoSandboxIsForbiddenEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            ClassLoader classLoader = getClass().getClassLoader();
            sandbox(ctx -> {
                SandboxExecutor<ClassLoader, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable ex = catchThrowableOfType(() -> WithJava.run(executor, EvilClassLoader.class, classLoader), RuleViolationError.class);
                assertThat(ex)
                        .hasMessageContaining("Cannot sandbox a ClassLoader");
                output.set(ex.getMessage());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.MaliciousClassTest.TestPassingClassLoaderIntoSandboxIsForbidden().assertResult(testResult);
        }
    }

    public static class EvilClassLoader implements Function<ClassLoader, String> {
        @Override
        public String apply(ClassLoader classLoader) {
            return classLoader.toString();
        }
    }

    /**
     * Fails due to compile-time dependency on sandbox.java.lang.Comparable, which is excluded from the DJVM jar
     */
//    public static class TestCannotInvokeSandboxMethodsExplicitlyEnclaveTest extends DJVMBase implements Test {
//        @Override
//        public Object apply(Object input) {
//            sandbox(ctx -> {
//                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
//                Throwable ex = catchThrowableOfType(() -> WithJava.run(executor, SelfSandboxing.class, "Victory is mine!"), SandboxClassLoadingException.class);
//                assertThat(ex)
//                        .isExactlyInstanceOf(SandboxClassLoadingException.class)
//                        .hasMessageContaining(Type.getInternalName(SelfSandboxing.class))
//                        .hasMessageContaining("Access to sandbox.java.lang.String.toDJVM(String) is forbidden.")
//                        .hasMessageContaining("Access to sandbox.java.lang.String.fromDJVM(String) is forbidden.")
//                        .hasMessageContaining("Casting to sandbox.java.lang.String is forbidden.")
//                        .hasNoCause();
//                return null;
//            });
//        }
//
//        @Override
//        public void assertResult(@NotNull byte[] testResult) {
//            new com.r3.conclave.jvmtester.djvm.asserters.MaliciousClassTest.TestCannotInvokeSandboxMethodsExplicitly.assertResult(testResult);
//        }
//    }
//
//    public static class SelfSandboxing implements Function<String, String> {
//        @SuppressWarnings("ConstantConditions")
//        @Override
//        public String apply(String message) {
//            return (String) (Object) sandbox.java.lang.String.toDJVM(
//                    sandbox.java.lang.String.fromDJVM((sandbox.java.lang.String) (Object) message)
//            );
//        }
//    }
}

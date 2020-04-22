package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SandboxObjectHashCodeJavaTest {

    public static class TestHashForArrayEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Object, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Integer> taskOutput = WithJava.run(executor, ArrayHashCode.class, null);
                assertThat(taskOutput.getResult()).isEqualTo(0xfed_c0de + 1);
                output.set(taskOutput.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeInt(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxObjectHashCodeJavaTest.TestHashForArray().assertResult(testResult);
        }
    }

    public static class TestHashForObjectInArrayEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Object, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Integer> taskOutput = WithJava.run(executor, ObjectInArrayHashCode.class, null);
                assertThat(taskOutput.getResult()).isEqualTo(0xfed_c0de + 1);
                output.set(taskOutput.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeInt(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxObjectHashCodeJavaTest.TestHashForObjectInArray().assertResult(testResult);
        }
    }

    public static class TestHashForNullObjectEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> new HashCode().apply(null));

            sandbox(ctx -> {
                SandboxExecutor<Object, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable throwable = catchThrowableOfType(() -> WithJava.run(executor, HashCode.class, null), NullPointerException.class);
                output.set(throwable.getClass().getCanonicalName());
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxObjectHashCodeJavaTest.TestHashForNullObject().assertResult(testResult);
        }
    }

    public static class TestHashForWrappedIntegerEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Object, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Integer> taskOutput = WithJava.run(executor, HashCode.class, 1234);
                assertThat(taskOutput.getResult()).isEqualTo(Integer.hashCode(1234));
                output.set(taskOutput.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeInt(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxObjectHashCodeJavaTest.TestHashForWrappedInteger().assertResult(testResult);
        }
    }

    public static class TestHashForWrappedStringEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Object, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Integer> taskOutput = WithJava.run(executor, HashCode.class, "Burble");
                assertThat(taskOutput.getResult()).isEqualTo("Burble".hashCode());
                output.set(taskOutput.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeInt(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxObjectHashCodeJavaTest.TestHashForWrappedString().assertResult(testResult);
        }
    }

    public static class ObjectInArrayHashCode implements Function<Object, Integer> {
        @Override
        public Integer apply(Object obj) {
            Object[] arr = new Object[1];
            arr[0] = new Object();
            return arr[0].hashCode();
        }
    }

    public static class ArrayHashCode implements Function<Object, Integer> {
        @SuppressWarnings("all")
        @Override
        public Integer apply(Object obj) {
            return new Object[0].hashCode();
        }
    }

    public static class HashCode implements Function<Object, Integer> {
        @Override
        public Integer apply(@Nullable Object obj) {
            return obj.hashCode();
        }
    }
}

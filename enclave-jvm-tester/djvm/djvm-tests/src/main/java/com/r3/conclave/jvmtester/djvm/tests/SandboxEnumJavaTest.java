package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.Log;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import com.r3.conclave.jvmtester.djvm.testsauxiliary.ExampleEnum;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SandboxEnumJavaTest {
    public static class TestEnumInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Integer, String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String[]> taskOutput = WithJava.run(executor, TransformEnum.class, 0);
                assertThat(taskOutput.getResult())
                        .isEqualTo(new String[]{ "ONE", "TWO", "THREE" });
                output.set(taskOutput.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxEnumJavaTest.TestEnumInsideSandbox().assertResult(testResult);
        }
    }

    public static class TransformEnum implements Function<Integer, String[]> {
        @Override
        public String[] apply(Integer input) {
            return Stream.of(ExampleEnum.values()).map(ExampleEnum::name).toArray(String[]::new);
        }
    }

    public static class TestReturnEnumFromSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ?> basicInputTask = ctx.getClassLoader().createBasicInput();
                    Object sandboxedInput = basicInputTask.apply("THREE");
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createRawTaskFactory();
                    Function<? super Object, ?> verifyTask = ctx.getClassLoader().createTaskFor(taskFactory, FetchEnum.class);
                    Object sandboxedOutput = verifyTask.apply(sandboxedInput);
                    assertThat(sandboxedOutput.getClass().getName()).isEqualTo("sandbox." + ExampleEnum.class.getName());
                    assertThat(sandboxedOutput.toString()).isEqualTo("THREE");
                    output.set(sandboxedOutput.toString());
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxEnumJavaTest.TestReturnEnumFromSandbox().assertResult(testResult);
        }
    }

    public static class FetchEnum implements Function<String, ExampleEnum> {
        public ExampleEnum apply(String input) {
            return ExampleEnum.valueOf(input);
        }
    }

    public static class TestWeCanIdentifyClassAsEnumEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<ExampleEnum, Boolean> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Boolean> taskOutput = WithJava.run(executor, AssertEnum.class, ExampleEnum.THREE);
                assertThat(taskOutput.getResult()).isTrue();
                output.set(taskOutput.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeBoolean(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxEnumJavaTest.TestWeCanIdentifyClassAsEnum().assertResult(testResult);
        }
    }

    public static class AssertEnum implements Function<ExampleEnum, Boolean> {
        @Override
        public Boolean apply(ExampleEnum input) {
            return input.getClass().isEnum();
        }
    }

    public static class TestWeCanCreateEnumMapEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<ExampleEnum, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Integer> taskOutput = WithJava.run(executor, UseEnumMap.class, ExampleEnum.TWO);
                assertThat(taskOutput.getResult()).isEqualTo(1);
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxEnumJavaTest.TestWeCanCreateEnumMap().assertResult(testResult);
        }
    }

    public static class UseEnumMap implements Function<ExampleEnum, Integer> {
        @Override
        public Integer apply(ExampleEnum input) {
            Map<ExampleEnum, String> map = new EnumMap<>(ExampleEnum.class);
            map.put(input, input.name());
            return map.size();
        }
    }

    public static class TestWeCanReadConstantEnumEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createRawTaskFactory();
                    Function<? super Object, ?> task = ctx.getClassLoader().createTaskFor(taskFactory, ConstantEnum.class);
                    Object sandboxedOutput = task.apply(null);

                    assertThat(sandboxedOutput.toString()).isEqualTo(ExampleEnum.ONE.toString());
                    output.set(sandboxedOutput.toString());
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxEnumJavaTest.TestWeCanReadConstantEnum().assertResult(testResult);
        }
    }

    public static class ConstantEnum implements Function<Object, ExampleEnum> {
        private final ExampleEnum value = ExampleEnum.ONE;

        @Override
        public ExampleEnum apply(Object input) {
            return value;
        }
    }

    public static class TestWeCanReadStaticConstantEnumEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ? extends Object>> taskFactory = ctx.getClassLoader().createRawTaskFactory();
                    Function<? super Object, ?> task = ctx.getClassLoader().createTaskFor(taskFactory, StaticConstantEnum.class);
                    Object sandboxedOutput = task.apply(null);
                    assertThat(sandboxedOutput.toString()).isEqualTo(ExampleEnum.TWO.toString());
                    output.set(sandboxedOutput.toString());
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxEnumJavaTest.TestWeCanReadStaticConstantEnum().assertResult(testResult);
        }
    }

    public static class StaticConstantEnum implements Function<Object, ExampleEnum> {
        private static final ExampleEnum VALUE = ExampleEnum.TWO;

        @Override
        public ExampleEnum apply(Object input) {
            return VALUE;
        }
    }
}

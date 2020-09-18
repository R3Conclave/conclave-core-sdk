package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.ExampleEnum;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.Log;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.Log;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.ExampleEnum;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class SandboxEnumJavaTest {
    public static class TestEnumInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String[] result = WithJava.run(taskFactory, TransformEnum.class, 0);
                    assertThat(result)
                            .isEqualTo(new String[]{ "ONE", "TWO", "THREE" });
                    output.set(result);
                } catch (Exception e) {
                    fail(e);
                }
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxEnumJavaTest.TestEnumInsideSandbox().assertResult(testResult);
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
                    Function<? super Object, ?> verifyTask = taskFactory.compose(ctx.getClassLoader().createSandboxFunction()).apply(FetchEnum.class);
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxEnumJavaTest.TestReturnEnumFromSandbox().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Boolean result = WithJava.run(taskFactory, AssertEnum.class, ExampleEnum.THREE);
                    assertThat(result).isTrue();
                    output.set(result);
                } catch (Exception e) {
                    fail(e);
                }
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxEnumJavaTest.TestWeCanIdentifyClassAsEnum().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Integer result = WithJava.run(taskFactory, UseEnumMap.class, ExampleEnum.TWO);
                    assertThat(result).isEqualTo(1);
                    output.set(result);
                } catch (Exception e) {
                    fail(e);
                }
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxEnumJavaTest.TestWeCanCreateEnumMap().assertResult(testResult);
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
                    Function<? super Object, ? extends Function<? super Object, ? extends Object>> rawTaskFactory = ctx.getClassLoader().createRawTaskFactory();
                    Function<? super Object, ?> task = rawTaskFactory.compose(ctx.getClassLoader().createSandboxFunction()).apply(ConstantEnum.class);
                    Object result = task.apply(null);
                    assertThat(result.toString()).isEqualTo(ExampleEnum.ONE.toString());
                    output.set(result.toString());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxEnumJavaTest.TestWeCanReadConstantEnum().assertResult(testResult);
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
                    Function<? super Object, ? extends Function<? super Object, ? extends Object>> rawTaskFactory = ctx.getClassLoader().createRawTaskFactory();
                    Function<? super Object, ?> task = rawTaskFactory.compose(ctx.getClassLoader().createSandboxFunction()).apply(StaticConstantEnum.class);
                    Object result = task.apply(null);
                    assertThat(result.toString()).isEqualTo(ExampleEnum.TWO.toString());
                    output.set(result.toString());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxEnumJavaTest.TestWeCanReadStaticConstantEnum().assertResult(testResult);
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

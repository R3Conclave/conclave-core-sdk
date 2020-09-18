package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class SandboxObjectHashCodeJavaTest {

    public static class TestHashForArrayEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Integer result = WithJava.run(taskFactory, ArrayHashCode.class, null);
                    assertThat(result).isEqualTo(0xfed_c0de + 1);
                    output.set(result);
                } catch(Exception e) {
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxObjectHashCodeJavaTest.TestHashForArray().assertResult(testResult);
        }
    }

    public static class TestHashForObjectInArrayEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Integer result = WithJava.run(taskFactory, ObjectInArrayHashCode.class, null);
                    assertThat(result).isEqualTo(0xfed_c0de + 1);
                    output.set(result);
                } catch(Exception e) {
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxObjectHashCodeJavaTest.TestHashForObjectInArray().assertResult(testResult);
        }
    }

    public static class TestHashForNullObjectEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> new HashCode().apply(null));

            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Throwable throwable = catchThrowableOfType(() -> WithJava.run(taskFactory, HashCode.class, null), NullPointerException.class);
                    output.set(throwable.getClass().getCanonicalName());
                } catch(Exception e) {
                    fail(e);
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxObjectHashCodeJavaTest.TestHashForNullObject().assertResult(testResult);
        }
    }

    public static class TestHashForWrappedIntegerEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Integer result = WithJava.run(taskFactory, HashCode.class, 1234);
                    assertThat(result).isEqualTo(Integer.hashCode(1234));
                    output.set(result);
                } catch(Exception e) {
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxObjectHashCodeJavaTest.TestHashForWrappedInteger().assertResult(testResult);
        }
    }

    public static class TestHashForWrappedStringEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Integer result = WithJava.run(taskFactory, HashCode.class, "Burble");
                    assertThat(result).isEqualTo("Burble".hashCode());
                    output.set(result);
                } catch(Exception e) {
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxObjectHashCodeJavaTest.TestHashForWrappedString().assertResult(testResult);
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

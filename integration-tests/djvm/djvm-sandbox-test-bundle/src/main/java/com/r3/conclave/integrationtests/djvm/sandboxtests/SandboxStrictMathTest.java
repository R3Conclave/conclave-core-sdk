package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestStrictMaxMinParameters;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestStrictMaxMinParameters;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;


public class SandboxStrictMathTest {
    public static class TestStrictMathHasNoRandomEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Throwable error = assertThrows(NoSuchMethodError.class, () -> WithJava.run(taskFactory, StrictRandom.class, 0));
                    assertThat(error)
                            .isExactlyInstanceOf(NoSuchMethodError.class)
                            .hasMessageContaining("random");
                    output.set(error.getMessage());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictMathHasNoRandom().assertResult(testResult);
        }
    }

    public static class StrictRandom implements Function<Integer, Double> {
        @Override
        public Double apply(Integer seed) {
            return StrictMath.random();
        }
    }

    public static class TestStrictMathHasTrigonometryEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Double[] result = WithJava.run(taskFactory, StrictTrigonometry.class, 0);
                    assertThat(result).isEqualTo(new Double[] {
                            0.0,
                            -1.0,
                            0.0,
                            StrictMath.PI / 2.0,
                            StrictMath.PI / 2.0,
                            0.0,
                            StrictMath.PI / 4.0
                    });
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
            return SerializationUtils.serializeDoubleArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictMathHasTrigonometry().assertResult(testResult);
        }
    }

    public static class StrictTrigonometry implements Function<Integer, Double[]> {
        @Override
        public Double[] apply(Integer input) {
            return new Double[] {
                    StrictMath.floor(StrictMath.sin(StrictMath.PI)),
                    StrictMath.cos(StrictMath.PI),
                    StrictMath.tan(0.0),
                    StrictMath.acos(0.0),
                    StrictMath.asin(1.0),
                    StrictMath.atan(0.0),
                    StrictMath.atan2(1.0, 1.0)
            };
        }
    }

    public static class TestStrictMathRootsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Double[] result = WithJava.run(taskFactory, StrictRoots.class, 64.0);
                    assertThat(result)
                            .isEqualTo(new Double[] { 8.0, 4.0, 13.0 });
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
            return SerializationUtils.serializeDoubleArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictMathRoots().assertResult(testResult);
        }
    }

    public static class StrictRoots implements Function<Double, Double[]> {
        @Override
        public Double[] apply(Double input) {
            return new Double[] {
                    StrictMath.sqrt(input),
                    StrictMath.cbrt(input),
                    StrictMath.hypot(5.0, 12.0)
            };
        }
    }

    public static class TestStrictMaxMinEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Object[] result = WithJava.run(taskFactory, StrictMaxMin.class, 100);
                    assertThat(result)
                            .isEqualTo(new Object[] { 100.0d, 0.0d, 100.0f, 0.0f, 100L, 0L, 100, 0 });
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
            Object[] result = (Object[]) output;
            TestStrictMaxMinParameters testStrictMaxMinParameters = TestStrictMaxMinParameters.newBuilder()
                    .setD1((Double) result[0])
                    .setD2((Double) result[1])
                    .setF1((Float) result[2])
                    .setF2((Float) result[3])
                    .setL1((Long) result[4])
                    .setL2((Long) result[5])
                    .setI1((Integer) result[6])
                    .setI2((Integer) result[7])
                    .build();
            return testStrictMaxMinParameters.toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictMaxMin().assertResult(testResult);
        }
    }

    public static class StrictMaxMin implements Function<Integer, Object[]> {
        @Override
        public Object[] apply(Integer input) {
            return new Object[] {
                    StrictMath.max((double) input, 0.0d),
                    StrictMath.min((double) input, 0.0d),
                    StrictMath.max((float) input, 0.0f),
                    StrictMath.min((float) input, 0.0f),
                    StrictMath.max((long) input, 0L),
                    StrictMath.min((long) input, 0L),
                    StrictMath.max(input, 0),
                    StrictMath.min(input, 0)
            };
        }
    }

    public static class TestStrictAbsoluteEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Object[] result = WithJava.run(taskFactory, StrictAbsolute.class, -100);
                    assertThat(result)
                            .isEqualTo(new Object[]{100.0d, 100.0f, 100L, 100});
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
            Object[] result = (Object[]) output;
            TestStrictMaxMinParameters testStrictMaxMinParameters = TestStrictMaxMinParameters.newBuilder()
                    .setD1((Double) result[0])
                    .setF1((Float) result[1])
                    .setL1((Long) result[2])
                    .setI1((Integer) result[3])
                    .build();
            return testStrictMaxMinParameters.toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictAbsolute().assertResult(testResult);
        }
    }

    public static class StrictAbsolute implements Function<Integer, Object[]> {
        @Override
        public Object[] apply(Integer input) {
            return new Object[] {
                    StrictMath.abs((double) input),
                    StrictMath.abs((float) input),
                    StrictMath.abs((long) input),
                    StrictMath.abs(input)
            };
        }
    }

    public static class TestStrictRoundEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Double[] inputs = new Double[] { 2019.3, 2020.9 };
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Object[] result = WithJava.run(taskFactory, StrictRound.class, inputs);
                    assertThat(result)
                            .isEqualTo(new Object[]{2019, 2019L, 2021, 2021L});
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
            Object[] result = (Object[]) output;
            TestStrictMaxMinParameters testStrictMaxMinParameters = TestStrictMaxMinParameters.newBuilder()
                    .setI1((Integer) result[0])
                    .setL1((Long) result[1])
                    .setI2((Integer) result[2])
                    .setL2((Long) result[3])
                    .build();
            return testStrictMaxMinParameters.toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictRound().assertResult(testResult);
        }
    }

    public static class StrictRound implements Function<Double[], Object[]> {
        @Override
        public Object[] apply(Double[] inputs) {
            List<Object> results = new ArrayList<>();
            for (Double input : inputs) {
                results.add(StrictMath.round(input.floatValue()));
                results.add(StrictMath.round(input));
            }
            return results.toArray();
        }
    }

    public static class TestStrictExponentsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        private static final double ERROR_DELTA = 1.0E-10;

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Double[] result = WithJava.run(taskFactory, StrictExponents.class, 0);
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(6);
                    assertThat(result[0]).isEqualTo(81.0);
                    assertThat(result[1]).isEqualTo(1.0);
                    assertThat(result[2]).isEqualTo(3.0);
                    assertThat(result[3]).isEqualTo(StrictMath.E, within(ERROR_DELTA));
                    assertThat(result[4]).isEqualTo(StrictMath.E - 1.0, within(ERROR_DELTA));
                    assertThat(result[5]).isEqualTo(1.0, within(ERROR_DELTA));
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
            return SerializationUtils.serializeDoubleArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictExponents().assertResult(testResult);
        }
    }

    public static class StrictExponents implements Function<Integer, Double[]> {
        @Override
        public Double[] apply(Integer input) {
            return new Double[] {
                    StrictMath.pow(3.0, 4.0),
                    StrictMath.log(StrictMath.E),
                    StrictMath.log10(1000.0),
                    StrictMath.exp(1.0),
                    StrictMath.expm1(1.0),
                    StrictMath.log1p(StrictMath.E - 1.0)
            };
        }
    }

    public static class TestStrictAnglesEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Double[] result = WithJava.run(taskFactory, StrictAngles.class, 0);
                    assertThat(result)
                            .isEqualTo(new Object[]{180.0, StrictMath.PI});
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
            return SerializationUtils.serializeDoubleArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictAngles().assertResult(testResult);
        }
    }

    public static class StrictAngles implements Function<Integer, Double[]> {
        @Override
        public Double[] apply(Integer input) {
            return new Double[] {
                    StrictMath.toDegrees(StrictMath.PI),
                    StrictMath.toRadians(180.0)
            };
        }
    }

    public static class TestStrictHyperbolicsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Double[] result = WithJava.run(taskFactory, StrictHyperbolics.class, 0.0);
                    assertThat(result)
                            .isEqualTo(new Double[]{ 0.0, 1.0, 0.0 });
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
            return SerializationUtils.serializeDoubleArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictHyperbolics().assertResult(testResult);
        }
    }

    public static class StrictHyperbolics implements Function<Double, Double[]> {
        @Override
        public Double[] apply(Double x) {
            return new Double[] {
                    StrictMath.sinh(x),
                    StrictMath.cosh(x),
                    StrictMath.tanh(x)
            };
        }
    }

    public static class TestStrictRemainderEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Double[] result = {
                            WithJava.run(taskFactory, StrictRemainder.class, 10.0),
                            WithJava.run(taskFactory, StrictRemainder.class, 7.0),
                            WithJava.run(taskFactory, StrictRemainder.class, 5.0)
                    };
                    assertThat(result[0]).isEqualTo(3.0);
                    assertThat(result[1]).isEqualTo(0.0);
                    assertThat(result[2]).isEqualTo(-2.0);
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
            return SerializationUtils.serializeDoubleArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxStrictMathTest.TestStrictRemainder().assertResult(testResult);
        }
    }

    public static class StrictRemainder implements Function<Double, Double> {
        @Override
        public Double apply(Double x) {
            return StrictMath.IEEEremainder(x, 7.0d);
        }
    }
}

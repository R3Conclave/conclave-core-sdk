package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class CapturingLambdaTest {
    private static final long BIG_NUMBER = 1234L;
    private static final int MULTIPLIER = 100;

    public static class TestCapturingLambdaEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Long, Long> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult success = WithJava.run(executor, CapturingLambda.class, BIG_NUMBER);
                assertThat(BIG_NUMBER * MULTIPLIER).isEqualTo(success.getResult());
                output.set(success.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeLong(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.CapturingLambdaTest.TestCapturingLambda().assertResult(testResult);
        }
    }

    public static class CapturingLambda implements Function<Long, Long> {
        private final BigDecimal value = new BigDecimal(MULTIPLIER);

        @Override
        public Long apply(Long input) {
            Function<BigDecimal, BigDecimal> lambda = value::multiply;
            return lambda.apply(new BigDecimal(input)).longValue();
        }
    }
}

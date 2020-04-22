package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    private static final String MESSAGE = "Round and round the rugged rocks...";
    private static final String BASE64 = Base64.getEncoder().encodeToString(MESSAGE.getBytes(UTF_8));

    public static class Base64ToBinaryTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, byte[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<byte[]> success = WithJava.run(executor, Base64ToBytes.class, BASE64);
                assertThat(success.getResult()).isNotNull();
                assertThat(new String(success.getResult(), UTF_8)).isEqualTo(MESSAGE);
                output.set(new String(success.getResult(), UTF_8));
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
            new com.r3.conclave.jvmtester.djvm.asserters.Base64Test.Base64ToBinaryTest().assertResult(testResult);
        }
    }

    public static class Base64ToBytes implements Function<String, byte[]> {
        @Override
        public byte[] apply(String input) {
            return Base64.getDecoder().decode(input);
        }
    }

    public static class BinaryToBase64TestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<byte[], String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String> success = WithJava.run(executor, BytesToBase64.class, MESSAGE.getBytes(UTF_8));
                assertThat(success.getResult()).isEqualTo(BASE64);
                output.set(success.getResult());
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
            new com.r3.conclave.jvmtester.djvm.asserters.Base64Test.BinaryToBase64Test().assertResult(testResult);
        }
    }

    public static class BytesToBase64 implements Function<byte[], String> {
        @Override
        public String apply(byte[] input) {
            return Base64.getEncoder().encodeToString(input);
        }
    }
}

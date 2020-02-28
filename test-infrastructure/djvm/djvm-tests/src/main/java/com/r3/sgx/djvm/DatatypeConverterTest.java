package com.r3.sgx.djvm;

import com.r3.sgx.djvm.util.SerializationUtils;
import com.r3.sgx.test.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.DatatypeConverter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class DatatypeConverterTest {
    private static final byte[] BINARY = new byte[]{ 0x1f, 0x2e, 0x3d, 0x4c, 0x5b, 0x6a, 0x70 };
    private static final String TEXT = "1F2E3D4C5B6A70";

    public static class HexToBinaryTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, byte[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<byte[]> success = WithJava.run(executor, HexToBytes.class, TEXT);
                assertThat(success.getResult()).isEqualTo(BINARY);
                output.set(success.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeByteArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.sgx.djvm.asserters.DatatypeConverterTest.HexToBinaryTest().assertResult(testResult);
        }
    }

    public static class HexToBytes implements Function<String, byte[]> {
        @Override
        public byte[] apply(String input) {
            return DatatypeConverter.parseHexBinary(input);
        }
    }

    public static class BinaryToHexTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();

            sandbox(ctx -> {
                SandboxExecutor<byte[], String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String> success = WithJava.run(executor, BytesToHex.class, BINARY);
                assertThat(success.getResult()).isEqualTo(TEXT);
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
            new com.r3.sgx.djvm.asserters.DatatypeConverterTest.BinaryToHexTest().assertResult(testResult);
        }
    }

    public static class BytesToHex implements Function<byte[], String> {
        @Override
        public String apply(byte[] input) {
            // Corda apparently depends on this returning in
            // uppercase in order not to break hash values.
            return DatatypeConverter.printHexBinary(input);
        }
    }
}

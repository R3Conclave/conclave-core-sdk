package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.DatatypeConverter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class DatatypeConverterTest {
    private static final byte[] BINARY = new byte[]{ 0x1f, 0x2e, 0x3d, 0x4c, 0x5b, 0x6a, 0x70 };
    private static final String TEXT = "1F2E3D4C5B6A70";

    public static class HexToBinaryTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    byte[] result = WithJava.run(taskFactory, HexToBytes.class, TEXT);
                    assertThat(result).isEqualTo(BINARY);
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
            return SerializationUtils.serializeByteArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.DatatypeConverterTest.HexToBinaryTest().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, BytesToHex.class, BINARY);
                    assertThat(result).isEqualTo(TEXT);
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
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.DatatypeConverterTest.BinaryToHexTest().assertResult(testResult);
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

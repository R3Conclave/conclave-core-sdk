package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class Base64Test {
    private static final String MESSAGE = "Round and round the rugged rocks...";
    private static final String BASE64 = Base64.getEncoder().encodeToString(MESSAGE.getBytes(UTF_8));

    public static class Base64ToBinaryTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    byte[] result = WithJava.run(taskFactory, Base64ToBytes.class, BASE64);
                    assertThat(result).isNotNull();
                    assertThat(new String(result, UTF_8)).isEqualTo(MESSAGE);
                    output.set(new String(result, UTF_8));
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.Base64Test.Base64ToBinaryTest().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, BytesToBase64.class, MESSAGE.getBytes(UTF_8));
                    assertThat(result).isEqualTo(BASE64);
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.Base64Test.BinaryToBase64Test().assertResult(testResult);
        }
    }

    public static class BytesToBase64 implements Function<byte[], String> {
        @Override
        public String apply(byte[] input) {
            return Base64.getEncoder().encodeToString(input);
        }
    }
}

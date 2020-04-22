package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.proto.ReadingDataTestResult;
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class DataInputStreamTest {
    private static final String MESSAGE = "Hello World!";
    private static final double BIG_FRACTION = 97323.38238232d;
    private static final long BIG_NUMBER = 81738392L;
    private static final int NUMBER = 123456;

    public static class ReadingDataTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object optionalInput) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                dos.writeLong(BIG_NUMBER);
                dos.writeInt(NUMBER);
                dos.writeUTF(MESSAGE);
                dos.writeDouble(BIG_FRACTION);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            InputStream input = new ByteArrayInputStream(baos.toByteArray());
            AtomicReference<Object> output = new AtomicReference<>();

            sandbox(ctx -> {
                SandboxExecutor<InputStream, Object[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Object[]> success = WithJava.run(executor, DataStreamer.class, input);
                Object[] result = success.getResult();
                assertThat(result).isEqualTo(new Object[]{
                        BIG_NUMBER,
                        NUMBER,
                        MESSAGE,
                        BIG_FRACTION
                });
                ReadingDataTestResult readingDataTestResult = ReadingDataTestResult.newBuilder()
                        .setBigNumber((long) result[0])
                        .setNumber((int) result[1])
                        .setMessage((String) result[2])
                        .setBigFraction((double) result[3])
                        .build();
                output.set(readingDataTestResult.toByteArray());
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
            new com.r3.conclave.jvmtester.djvm.asserters.DataInputStreamTest.ReadingDataTest().assertResult(testResult);
        }
    }

    public static class DataStreamer implements Function<InputStream, Object[]> {
        @Override
        public Object[] apply(InputStream input) {
            try (DataInputStream dis = new DataInputStream(input)) {
                return new Object[] {
                        dis.readLong(),
                        dis.readInt(),
                        dis.readUTF(),
                        dis.readDouble()
                };
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

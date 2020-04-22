package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class BitSetTest {
    private static final byte[] BITS = new byte[] { 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08 };
    private static final int POSITION = 16;

    public static class CreateBitSetTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            BitSet bitset = BitSet.valueOf(BITS);

            sandbox(ctx -> {
                SandboxExecutor<byte[], int[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<int[]> success = WithJava.run(executor, CreateBitSet.class, BITS);
                assertThat(success.getResult())
                        .isEqualTo(new int[] {
                                bitset.length(),
                                bitset.cardinality(),
                                bitset.size(),
                                bitset.nextClearBit(POSITION),
                                bitset.previousClearBit(POSITION),
                                bitset.nextSetBit(POSITION),
                                bitset.previousSetBit(POSITION)
                        });
                output.set(success.getResult());
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeIntArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.BitSetTest.CreateBitSetTest().assertResult(testResult);
        }
    }

    public static class CreateBitSet implements Function<byte[], int[]> {
        @Override
        public int[] apply(byte[] bytes) {
            BitSet bits = BitSet.valueOf(bytes);
            return new int[] {
                    bits.length(),
                    bits.cardinality(),
                    bits.size(),
                    bits.nextClearBit(POSITION),
                    bits.previousClearBit(POSITION),
                    bits.nextSetBit(POSITION),
                    bits.previousSetBit(POSITION)
            };
        }
    }
}

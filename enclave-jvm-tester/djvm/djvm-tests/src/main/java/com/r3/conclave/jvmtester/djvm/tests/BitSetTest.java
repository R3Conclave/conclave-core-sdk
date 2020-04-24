package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class BitSetTest {
    private static final byte[] BITS = new byte[] { 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08 };
    private static final int POSITION = 16;

    public static class CreateBitSetTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            BitSet bitset = BitSet.valueOf(BITS);

            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    int[] result = WithJava.run(taskFactory, CreateBitSet.class, BITS);
                    assertThat(result)
                            .isEqualTo(new int[] {
                                    bitset.length(),
                                    bitset.cardinality(),
                                    bitset.size(),
                                    bitset.nextClearBit(POSITION),
                                    bitset.previousClearBit(POSITION),
                                    bitset.nextSetBit(POSITION),
                                    bitset.previousSetBit(POSITION)
                            });
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

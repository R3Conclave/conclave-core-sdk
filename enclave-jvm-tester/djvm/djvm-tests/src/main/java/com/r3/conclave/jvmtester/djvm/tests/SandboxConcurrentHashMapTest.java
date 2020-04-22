package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class SandboxConcurrentHashMapTest {

    public static class TestJoiningIterableInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[]{ "one", "One", "ONE" };
            sandbox(ctx -> {
                SandboxExecutor<String[], String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String> success = WithJava.run(executor, CreateMap.class, inputs);
                assertThat(success.getResult()).isEqualTo("[one has 3]");
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxConcurrentHashMapTest.TestJoiningIterableInsideSandbox().assertResult(testResult);
        }
    }

    public static class CreateMap implements Function<String[], String> {
        private final ConcurrentMap<String, Data> map = new ConcurrentHashMap<>();

        @Override
        public String apply(@NotNull String[] strings) {
            for (String s : strings) {
                Data data = map.computeIfAbsent(s.toLowerCase(), k -> new Data(0));
                data.increment();
            }

            StringBuilder result = new StringBuilder();
            map.forEach((k, v) -> result.append('[').append(k).append(" has ").append(v).append(']'));
            return result.toString();
        }

        private static class Data {
            private int value;

            Data(int value) {
                this.value = value;
            }

            int getValue() {
                return value;
            }

            void increment() {
                ++value;
            }

            @Override
            public String toString() {
                return Integer.toString(getValue());
            }
        }
    }

    public static class TestStreamOfKeysEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Integer[] inputs = new Integer[]{ 1, 2, 3 };
            sandbox(ctx -> {
                SandboxExecutor<Integer[], Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<Integer> success = WithJava.run(executor, KeyStreamMap.class, inputs);
                assertThat(success.getResult()).isEqualTo(6);
                output.set(success.getResult());
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxConcurrentHashMapTest.TestStreamOfKeys().assertResult(testResult);
        }
    }

    public static class KeyStreamMap implements Function<Integer[], Integer> {
        private final ConcurrentMap<Integer, String> map = new ConcurrentHashMap<>();

        @Override
        public Integer apply(@NotNull Integer[] input) {
            for (Integer i : input) {
                map.putIfAbsent(i, Integer.toString(i));
            }
            return map.keySet().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }
    }
}

package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class SandboxConcurrentHashMapTest {

    public static class TestJoiningIterableInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[]{ "one", "One", "ONE" };
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, CreateMap.class, inputs);
                    assertThat(result).isEqualTo("[one has 3]");
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxConcurrentHashMapTest.TestJoiningIterableInsideSandbox().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Integer result = WithJava.run(taskFactory, KeyStreamMap.class, inputs);
                    assertThat(result).isEqualTo(6);
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
            return SerializationUtils.serializeInt(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxConcurrentHashMapTest.TestStreamOfKeys().assertResult(testResult);
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

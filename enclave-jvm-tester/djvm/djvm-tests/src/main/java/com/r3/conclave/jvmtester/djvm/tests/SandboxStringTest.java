package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import net.corda.djvm.execution.SandboxRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class SandboxStringTest {
    private static final String UNICODE_MESSAGE = "Goodbye, Cruel World! \u1F4A9";
    private static final String HELLO_WORLD = "Hello World!";

    public static class TestJoiningIterableInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[]{"one", "two", "three"};
            sandbox(ctx -> {
                SandboxExecutor<String[], String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String> success = WithJava.run(executor, JoinIterableStrings.class, inputs);
                assertThat(success.getResult()).isEqualTo("one+two+three");
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestJoiningIterableInsideSandbox().assertResult(testResult);
        }
    }

    public static class JoinIterableStrings implements Function<String[], String> {
        @Override
        public String apply(String[] input) {
            return String.join("+", asList(input));
        }
    }

    public static class TestJoiningVarargInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[]{"ONE", "TWO", "THREE"};
            sandbox(ctx -> {
                SandboxExecutor<String[], String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String> success = WithJava.run(executor, JoinVarargStrings.class, inputs);
                assertThat(success.getResult()).isEqualTo("ONE+TWO+THREE");
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestJoiningVarargInsideSandbox().assertResult(testResult);
        }
    }

    public static class JoinVarargStrings implements Function<String[], String> {
        @Override
        public String apply(String[] input) {
            return String.join("+", input);
        }
    }

    public static class TestStringConstantEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                String result = WithJava.run(executor, StringConstant.class, "Wibble!").getResult();
                assertThat(result)
                        .isEqualTo("Wibble!");
                output.set(result);
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestStringConstant().assertResult(testResult);
        }
    }

    public static class StringConstant implements Function<String, String> {
        @SuppressWarnings("all")
        @Override
        public String apply(String input) {
            String constant = input.intern();
            if (!constant.equals(input)) {
                throw new IllegalArgumentException("String constant has wrong value: '" + constant + '\'');
            } else if (constant != "Wibble!") {
                throw new IllegalArgumentException("String constant has not been interned");
            }
            return constant;
        }
    }

    public static class EncodeStringWithUnknownCharsetEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, byte[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable exception = catchThrowableOfType(() -> WithJava.run(executor, GetEncoding.class, "Nonsense-101"), RuntimeException.class);
                assertThat(exception)
                        .isExactlyInstanceOf(SandboxRuntimeException.class)
                        .hasCauseExactlyInstanceOf(UnsupportedEncodingException.class)
                        .hasMessage("Nonsense-101");
                output.set(exception.getMessage());
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.EncodeStringWithUnknownCharset().assertResult(testResult);
        }
    }

    public static class GetEncoding implements Function<String, byte[]> {
        @Override
        public byte[] apply(String charsetName) {
            try {
                return UNICODE_MESSAGE.getBytes(charsetName);
            } catch (UnsupportedEncodingException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static class DecodeStringWithCharsetEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            String[] inputs = {"UTF-8", "UTF-16", "UTF-32"};
            ArrayList<String> outputs = new ArrayList<>(inputs.length);
            sandbox(ctx -> {
                for (String charsetName : inputs) {
                    SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                    ExecutionSummaryWithResult<String> success = WithJava.run(executor, CreateString.class, charsetName);
                    assertThat(success.getResult()).isEqualTo(UNICODE_MESSAGE);
                    outputs.add(success.getResult());
                }
                return null;
            });
            return outputs;
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringList(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.DecodeStringWithCharset().assertResult(testResult);
        }
    }

    public static class CreateString implements Function<String, String> {
        @Override
        public String apply(String charsetName) {
            try {
                return new String(UNICODE_MESSAGE.getBytes(Charset.forName(charsetName)), charsetName);
            } catch (UnsupportedEncodingException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static class TestCaseInsensitiveComparisonEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                int[] outputs = new int[] { WithJava.run(executor, CaseInsensitiveCompare.class, "hello world!").getResult(),
                        WithJava.run(executor, CaseInsensitiveCompare.class, "GOODBYE!").getResult(),
                        WithJava.run(executor, CaseInsensitiveCompare.class, "zzzzz...").getResult()
                };
                output.set(outputs);
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestCaseInsensitiveComparison().assertResult(testResult);
        }
    }

    public static class CaseInsensitiveCompare implements Function<String, Integer> {
        @Override
        public Integer apply(String str) {
            return String.CASE_INSENSITIVE_ORDER.compare(str, HELLO_WORLD);
        }
    }

    public static class TestStreamEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[] {"dog", "cat", "mouse", "squirrel"};
            sandbox(ctx -> {
                SandboxExecutor<String[], String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                String result = WithJava.run(executor, Concatenate.class, inputs).getResult();
                assertThat(result)
                        .isEqualTo("{dog + cat + mouse + squirrel}");
                output.set(result);
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestStream().assertResult(testResult);
        }
    }

    public static class Concatenate implements Function<String[], String> {
        @Override
        public String apply(String[] inputs) {
            return stream(inputs).collect(joining(" + ", "{", "}"));
        }
    }

    public static class TestSortingEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = Stream.of("Wolf", "Cat", "Tree", "Pig").map(String::toUpperCase).toArray(String[]::new);
            sandbox(ctx -> {
                SandboxExecutor<String[], String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                String[] result = WithJava.run(executor, Sorted.class, inputs).getResult();
                assertThat(result)
                        .containsExactly("CAT", "PIG", "TREE", "WOLF");
                output.set(result);
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestSorting().assertResult(testResult);
        }
    }

    public static class Sorted implements Function<String[], String[]> {
        @Override
        public String[] apply(String[] inputs) {
            List<String> list = asList(inputs);
            list.sort(null);
            return list.toArray(new String[0]);
        }
    }

    public static class TestComplexStreamEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[] { "one", "two", "three", "four", "five" };
            sandbox(ctx -> {
                SandboxExecutor<String[], String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                String[] result = WithJava.run(executor, ComplexStream.class, inputs).getResult();
                assertThat(result)
                        .containsExactly("ONE", "TWO", "THREE", "FOUR", "FIVE");
                output.set(result);
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestComplexStream().assertResult(testResult);
        }
    }

    public static class ComplexStream implements Function<String[], String[]> {
        @Override
        public String[] apply(String[] inputs) {
            return Stream.of(inputs).map(String::toUpperCase).toArray(String[]::new);
        }
    }

    public static class TestSpliteratorEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[] { "one", "two", "three", "four" };
            sandbox(ctx -> {
                SandboxExecutor<String[], String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                String[] result = WithJava.run(executor, Spliterate.class, inputs).getResult();
                assertThat(result)
                        .containsExactlyInAnyOrder("one+two", "three+four");
                output.set(result);
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringArray(output   ).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxStringTest.TestSpliterator().assertResult(testResult);
        }
    }

    public static class Spliterate implements Function<String[], String[]> {
        @Override
        public String[] apply(String[] inputs) {
            Spliterator<String> split1 = asList(inputs).spliterator();
            Spliterator<String> split2 = split1.trySplit();
            return new String[] { join(split1), join(split2) };
        }

        private String join(Spliterator<String> split) {
            return StreamSupport.stream(split, false).collect(joining("+"));
        }
    }
}

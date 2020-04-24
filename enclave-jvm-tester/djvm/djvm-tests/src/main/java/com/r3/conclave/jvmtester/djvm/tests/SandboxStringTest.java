package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class SandboxStringTest {
    private static final String UNICODE_MESSAGE = "Goodbye, Cruel World! \u1F4A9";
    private static final String HELLO_WORLD = "Hello World!";

    public static class TestJoiningIterableInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            String[] inputs = new String[]{"one", "two", "three"};
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, JoinIterableStrings.class, inputs);
                    assertThat(result).isEqualTo("one+two+three");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, JoinVarargStrings.class, inputs);
                    assertThat(result).isEqualTo("ONE+TWO+THREE");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, StringConstant.class, "Wibble!");
                    assertThat(result)
                            .isEqualTo("Wibble!");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Throwable exception = assertThrows(RuntimeException.class, () -> WithJava.run(taskFactory, GetEncoding.class, "Nonsense-101"));
                    assertThat(exception)
                            .isExactlyInstanceOf(SandboxRuntimeException.class)
                            .hasCauseExactlyInstanceOf(UnsupportedEncodingException.class)
                            .hasMessage("Nonsense-101");
                    output.set(exception.getMessage());
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
                try {
                    for (String charsetName : inputs) {
                        TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                        String result = WithJava.run(taskFactory, CreateString.class, charsetName);
                        assertThat(result).isEqualTo(UNICODE_MESSAGE);
                        outputs.add(result);
                    }
                } catch (Exception e) {
                    fail(e);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    int[] outputs = new int[] { WithJava.run(taskFactory, CaseInsensitiveCompare.class, "hello world!"),
                            WithJava.run(taskFactory, CaseInsensitiveCompare.class, "GOODBYE!"),
                            WithJava.run(taskFactory, CaseInsensitiveCompare.class, "zzzzz...")
                    };
                    output.set(outputs);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, Concatenate.class, inputs);
                    assertThat(result)
                            .isEqualTo("{dog + cat + mouse + squirrel}");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String[] result = WithJava.run(taskFactory, Sorted.class, inputs);
                    assertThat(result)
                            .containsExactly("CAT", "PIG", "TREE", "WOLF");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String[] result = WithJava.run(taskFactory, ComplexStream.class, inputs);
                    assertThat(result)
                            .containsExactly("ONE", "TWO", "THREE", "FOUR", "FIVE");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String[] result = WithJava.run(taskFactory, Spliterate.class, inputs);
                    assertThat(result)
                            .containsExactlyInAnyOrder("one+two", "three+four");
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

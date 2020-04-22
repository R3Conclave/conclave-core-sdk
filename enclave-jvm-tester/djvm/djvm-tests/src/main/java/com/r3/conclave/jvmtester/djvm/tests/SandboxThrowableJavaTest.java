package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.Log;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.r3.conclave.jvmtester.djvm.tests.util.Utilities.throwRuleViolationError;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowableOfType;

public class SandboxThrowableJavaTest {

    public static class TestUserExceptionHandlingEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String[]> taskOutput = WithJava.run(executor, ThrowAndCatchJavaExample.class, "Hello World!");
                assertThat(taskOutput.getResult())
                        .isEqualTo(new String[]{ "FIRST FINALLY", "BASE EXCEPTION", "Hello World!", "SECOND FINALLY" });
                output.set(taskOutput.getResult());
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxThrowableJavaTest.TestUserExceptionHandling().assertResult(testResult);
        }
    }

    public static class TestCheckedExceptionsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                String[] outputs = new String[] {
                        WithJava.run(executor, JavaWithCheckedExceptions.class, "http://localhost:8080/hello/world").getResult(),
                        WithJava.run(executor, JavaWithCheckedExceptions.class, "nasty string").getResult()
                };
                assertThat(outputs[0]).isEqualTo("/hello/world");
                assertThat(outputs[1]).isEqualTo("CATCH:Illegal character in path at index 5: nasty string");
                output.set(outputs);
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxThrowableJavaTest.TestCheckedExceptions().assertResult(testResult);
        }
    }

    public static class TestMultiCatchExceptionsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<Integer, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ArrayList<String> outputs = new ArrayList<>();
                {
                    String result = WithJava.run(executor, WithMultiCatchExceptions.class, 1).getResult();
                    assertThat(result).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyExampleException:1");
                    outputs.add(result);
                }
                {

                    String result = WithJava.run(executor, WithMultiCatchExceptions.class, 2).getResult();
                    outputs.add(result);
                    assertThat(result).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyOtherException:2");
                }
                {
                    Throwable exception = catchThrowableOfType(() -> WithJava.run(executor, WithMultiCatchExceptions.class, 3), RuntimeException.class);
                    assertThat(exception)
                            .isExactlyInstanceOf(RuntimeException.class)
                            .hasMessage("sandbox.com.r3.conclave.jvmtester.djvm.tests.BigTroubleException -> 3")
                            .hasCauseExactlyInstanceOf(Exception.class);
                    assertThat(exception.getCause())
                            .hasMessage("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyBaseException -> sandbox.com.r3.conclave.jvmtester.djvm.tests.BigTroubleException=3");
                    outputs.add(exception.getMessage());
                }
                {
                    Throwable exception = catchThrowableOfType(() -> WithJava.run(executor, WithMultiCatchExceptions.class, 4), IllegalArgumentException.class);
                    assertThat(exception)
                            .hasMessage("4")
                            .hasCauseExactlyInstanceOf(Exception.class);
                    assertThat(exception.getCause())
                            .hasMessage("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyBaseException -> sandbox.java.lang.IllegalArgumentException=4");
                    outputs.add(exception.getMessage());
                }
                {
                    Throwable exception = catchThrowableOfType(() -> WithJava.run(executor, WithMultiCatchExceptions.class, 1000), UnsupportedOperationException.class);
                    assertThat(exception)
                            .hasMessage("Unknown")
                            .hasNoCause();
                    outputs.add(exception.getMessage());
                }
                output.set(outputs);
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringList(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxThrowableJavaTest.TestMultiCatchExceptions().assertResult(testResult);
        }
    }

    public static class TestMultiCatchWithDisallowedExceptionsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                    ExecutionSummaryWithResult<String> success = WithJava.run(executor, WithMultiCatchDisallowedExceptions.class, "Hello World!");
                    assertThat(success.getResult()).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyExampleException:Hello World!");
                    output.set(success.getResult());
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxThrowableJavaTest.TestMultiCatchWithDisallowedExceptions().assertResult(testResult);
        }
    }

    public static class TestSuppressedJvmExceptionsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable exception = catchThrowableOfType(() -> WithJava.run(executor, WithSuppressedJvmExceptions.class, "Hello World!"), IllegalArgumentException.class);
                assertThat(exception)
                        .hasCauseExactlyInstanceOf(IOException.class)
                        .hasMessage("READ=Hello World!");
                assertThat(exception.getCause())
                        .hasMessage("READ=Hello World!");
                assertThat(exception.getCause().getSuppressed())
                        .hasSize(1)
                        .allMatch(t -> t instanceof IOException && t.getMessage().equals("CLOSING"));
                output.set(new String[] {
                        exception.getMessage(), exception.getCause().getMessage(), exception.getCause().getSuppressed()[0].getMessage()
                });
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxThrowableJavaTest.TestSuppressedJvmExceptions().assertResult(testResult);
        }
    }

    public static class TestSuppressedUserExceptionsEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable exception = catchThrowableOfType(() -> WithJava.run(executor, WithSuppressedUserExceptions.class, "Hello World!"), IllegalArgumentException.class);
                assertThat(exception)
                        .hasMessage("THROW: Hello World!")
                        .hasCauseExactlyInstanceOf(Exception.class);
                assertThat(exception.getCause())
                        .hasMessage("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyExampleException -> THROW: Hello World!");
                assertThat(exception.getCause().getSuppressed())
                        .hasSize(1)
                        .allMatch(t -> t instanceof RuntimeException
                                && t.getMessage().equals("sandbox.com.r3.conclave.jvmtester.djvm.tests.BigTroubleException -> BadResource: Hello World!"));
                output.set(new String[] {
                        exception.getMessage(), exception.getCause().getMessage(), exception.getCause().getSuppressed()[0].getMessage()
                });
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxThrowableJavaTest.TestSuppressedUserExceptions().assertResult(testResult);
        }
    }

    public static class ThrowAndCatchJavaExample implements Function<String, String[]> {
        @Override
        public String[] apply(String input) {
            List<String> data = new LinkedList<>();
            try {
                try {
                    throw new MyExampleException(input);
                } finally {
                    data.add("FIRST FINALLY");
                }
            } catch (MyBaseException e) {
                data.add("BASE EXCEPTION");
                data.add(e.getMessage());
            } catch (Exception e) {
                data.add("NOT THIS ONE!");
            } finally {
                data.add("SECOND FINALLY");
            }

            return data.toArray(new String[0]);
        }
    }

    public static class JavaWithCheckedExceptions implements Function<String, String> {
        @Override
        public String apply(String input) {
            try {
                return new URI(input).getPath();
            } catch (URISyntaxException e) {
                return "CATCH:" + e.getMessage();
            }
        }
    }

    public static class WithMultiCatchExceptions implements Function<Integer, String> {
        @Override
        public String apply(@NotNull Integer input) {
            try {
                switch(input) {
                    case 1: throw new MyExampleException("1");
                    case 2: throw new MyOtherException("2");
                    case 3: throw new BigTroubleException("3");
                    case 4: throw new IllegalArgumentException("4");
                    default: throw new UnsupportedOperationException("Unknown");
                }
            } catch (MyExampleException | MyOtherException e) {
                // Common exception type is MyBaseException
                return e.getClass().getName() + ':' + e.getMessage();
            } catch (BigTroubleException | IllegalArgumentException e) {
                // Common exception type is RuntimeException
                e.initCause(new MyBaseException(e.getClass().getName() + '=' + e.getMessage()));
                throw e;
            }
        }
    }

    public static class WithMultiCatchDisallowedExceptions implements Function<String, String> {
        @Override
        public String apply(@NotNull String input) {
            try {
                if (!input.isEmpty()) {
                    throw new MyExampleException(input);
                } else {
                    throwRuleViolationError();
                    return "FAIL";
                }
            } catch (MyExampleException | ThreadDeath e) {
                return e.getClass().getName() + ':' + e.getMessage();
            }
        }
    }

    public static class WithSuppressedJvmExceptions implements Function<String, String> {
        @Override
        public String apply(String input) {
            try (BadReader reader = new BadReader(input)) {
                throw new IOException("READ=" + reader.getName());
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }

    public interface MyResource extends AutoCloseable {
        String getName();
    }

    public static class BadReader implements MyResource {
        private final String name;

        BadReader(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("CLOSING");
        }
    }

    public static class WithSuppressedUserExceptions implements Function<String, String> {
        @Override
        public String apply(String input) {
            try (MyResource resource = new BadResource(input)) {
                throw new MyExampleException("THROW: " + resource.getName());
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }

    public static class BadResource implements MyResource {
        private final String name;

        BadResource(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void close() {
            throw new BigTroubleException("BadResource: " + getName());
        }
    }
}

package com.r3.sgx.djvm;

import com.r3.sgx.djvm.util.Log;
import com.r3.sgx.djvm.util.SerializationUtils;
import com.r3.sgx.test.EnclaveJvmTest;
import net.corda.djvm.rewiring.SandboxClassLoader;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicInputOutputTest {
    private static final String MESSAGE = "Hello World!";
    private static final Long BIG_NUMBER = 123456789000L;

    public static class BasicInputEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ?> inputTask = ctx.getClassLoader().createBasicInput();
                    Object sandboxObject = inputTask.apply(MESSAGE);
                    assertThat("sandbox.java.lang.String").isEqualTo(sandboxObject.getClass().getName());
                    assertThat(MESSAGE).isEqualTo(sandboxObject.toString());
                    output.set(sandboxObject.toString());
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
            new com.r3.sgx.djvm.asserters.BasicInputOutputTest.BasicInput().assertResult(testResult);
        }
    }

    public static class BasicOutputEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ?> inputTask = ctx.getClassLoader().createBasicInput();
                    Object sandboxObject = inputTask.apply(BIG_NUMBER);

                    Function<? super Object, ?> outputTask = ctx.getClassLoader().createBasicOutput();
                    Object result = outputTask.apply(sandboxObject);
                    assertThat(result)
                            .isExactlyInstanceOf(Long.class)
                            .isEqualTo(BIG_NUMBER);
                    output.set(result);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeLong(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.sgx.djvm.asserters.BasicInputOutputTest.BasicOutput().assertResult(testResult);
        }
    }

    public static class ImportTaskEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    SandboxClassLoader classLoader = ctx.getClassLoader();
                    Function<? super String, ?> importTask = classLoader.createForImport(new DoMagic());

                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = classLoader.createRawTaskFactory();
                    Function<? super String, ?> rawTask = taskFactory.apply(importTask);

                    String applyResult = new DoMagic().apply(MESSAGE);
                    Object rawTaskApplyResult = rawTask.apply(MESSAGE);
                    assertThat(applyResult).isEqualTo(rawTaskApplyResult);
                    output.set(rawTaskApplyResult);
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
            new com.r3.sgx.djvm.asserters.BasicInputOutputTest.ImportTask().assertResult(testResult);
        }
    }

    public static class DoMagic implements Function<String, String> {
        @Override
        public String apply(String input) {
            return String.format(">>> %s <<<", input);
        }
    }
}

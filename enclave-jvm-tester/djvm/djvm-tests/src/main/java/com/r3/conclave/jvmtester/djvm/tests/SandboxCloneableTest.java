package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class SandboxCloneableTest {

    public static class TestCloningInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String> success = WithJava.run(executor, CloningMachine.class, "Jango Fett");
                assertThat(success.getResult()).isEqualTo("Jango Fett");
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxCloneableTest.TestCloningInsideSandbox().assertResult(testResult);
        }
    }

    public static class Soldier implements Cloneable {
        private final String name;

        Soldier(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public Soldier clone() throws CloneNotSupportedException {
            return (Soldier) super.clone();
        }
    }

    public static class CloningMachine implements Function<String, String> {
        @Override
        public String apply(String subjectName) {
            Soldier soldier = new Soldier(subjectName);
            try {
                return soldier.clone().getName();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    public static class TestFailedCloningInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable throwable = catchThrowableOfType(() -> WithJava.run(executor, ForceProjector.class, "Obi Wan"), RuntimeException.class);
                assertThat(throwable)
                        .isExactlyInstanceOf(RuntimeException.class)
                        .hasCauseExactlyInstanceOf(CloneNotSupportedException.class)
                        .hasMessage("sandbox." + Jedi.class.getTypeName());
                output.set(throwable.getMessage());
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
            new com.r3.conclave.jvmtester.djvm.asserters.SandboxCloneableTest.TestFailedCloningInsideSandbox().assertResult(testResult);
        }
    }

    public static class Jedi {
        private final String name;

        Jedi(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public Jedi clone() throws CloneNotSupportedException {
            return (Jedi) super.clone();
        }
    }

    public static class ForceProjector implements Function<String, String> {
        @Override
        public String apply(String subjectName) {
            Jedi jedi = new Jedi(subjectName);
            try {
                return jedi.clone().getName();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }
}

package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class SandboxCloneableTest {

    public static class TestCloningInsideSandboxEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, CloningMachine.class, "Jango Fett");
                    assertThat(result).isEqualTo("Jango Fett");
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Throwable throwable = assertThrows(RuntimeException.class, () -> WithJava.run(taskFactory, ForceProjector.class, "Obi Wan"));
                    assertThat(throwable)
                            .isExactlyInstanceOf(RuntimeException.class)
                            .hasCauseExactlyInstanceOf(CloneNotSupportedException.class)
                            .hasMessage("sandbox." + Jedi.class.getTypeName());
                    output.set(throwable.getMessage());
                } catch(Exception e) {
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

package com.r3.sgx.djvm;

import com.r3.sgx.djvm.util.SerializationUtils;
import com.r3.sgx.test.EnclaveJvmTest;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class SecurityManagerTest {

    public static class TestReplacingSecurityManagerEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                RuntimeException ex = catchThrowableOfType(() -> WithJava.run(executor, ReplacingSecurityManager.class, ""), RuntimeException.class);
                assertThat(ex)
                        .isExactlyInstanceOf(RuntimeException.class)
                        .hasMessage("sandbox.java.security.AccessControlException -> access denied")
                        .hasNoCause();
                output.set(ex.getMessage());
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
            new com.r3.sgx.djvm.asserters.SecurityManagerTest.TestReplacingSecurityManager().assertResult(testResult);
        }
    }

    public static class ReplacingSecurityManager implements Function<String, String> {
        @Override
        public String apply(String s) {
            System.setSecurityManager(new SecurityManager() {});
            return null;
        }
    }
}

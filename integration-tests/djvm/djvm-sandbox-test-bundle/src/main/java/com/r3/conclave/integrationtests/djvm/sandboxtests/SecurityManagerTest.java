package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.fail;

public class SecurityManagerTest {

    public static class TestReplacingSecurityManagerEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    RuntimeException ex = catchThrowableOfType(() -> WithJava.run(taskFactory, ReplacingSecurityManager.class, ""), RuntimeException.class);
                    assertThat(ex)
                            .isExactlyInstanceOf(RuntimeException.class)
                            .hasMessage("sandbox.java.security.AccessControlException -> access denied")
                            .hasNoCause();
                    output.set(ex.getMessage());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SecurityManagerTest.TestReplacingSecurityManager().assertResult(testResult);
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

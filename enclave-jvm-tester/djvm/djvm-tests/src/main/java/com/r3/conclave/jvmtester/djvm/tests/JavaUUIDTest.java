package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.Log;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaUUIDTest {
    public static class UUIDTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            UUID uuid = UUID.randomUUID();
            sandbox(ctx -> {
                try {
                    Object sandboxUUID = ctx.getClassLoader().createBasicInput().apply(uuid);
                    assertThat("sandbox.java.util.UUID").isEqualTo(sandboxUUID.getClass().getName());
                    assertThat(uuid.toString()).isEqualTo(sandboxUUID.toString());

                    Object revert = ctx.getClassLoader().createBasicOutput().apply(sandboxUUID);
                    assertThat(uuid).isNotSameAs(revert);
                    assertThat(uuid).isEqualTo(revert);
                    output.set(sandboxUUID.toString());
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
            new com.r3.conclave.jvmtester.djvm.asserters.JavaUUIDTest.UUIDTest().assertResult(testResult);
        }
    }
}

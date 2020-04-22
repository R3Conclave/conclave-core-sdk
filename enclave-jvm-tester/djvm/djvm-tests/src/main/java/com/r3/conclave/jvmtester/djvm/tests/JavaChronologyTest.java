package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import org.jetbrains.annotations.NotNull;

import java.time.chrono.Chronology;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaChronologyTest {
    public static class AvailableChronologiesTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Function<? super String, String[]> chronologyTask = typedTaskFor(ctx.getClassLoader(), taskFactory, GetChronologyNames.class);
                    String[] chronologies = chronologyTask.apply(null);
                    assertThat(chronologies).contains(
                            "Hijrah-umalqura",
                            "ISO",
                            "Japanese",
                            "Minguo",
                            "ThaiBuddhist"
                    );
                    output.set(chronologies);
                } catch (Exception e) {
                    throw new RuntimeException(e);
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
            new com.r3.conclave.jvmtester.djvm.asserters.JavaChronologyTest.AvailableChronologiesTest().assertResult(testResult);
        }
    }

    public static class GetChronologyNames implements Function<String, String[]> {
        @Override
        public String[] apply(String s) {
            return Chronology.getAvailableChronologies().stream()
                    .map(Chronology::getId)
                    .toArray(String[]::new);
        }
    }
}

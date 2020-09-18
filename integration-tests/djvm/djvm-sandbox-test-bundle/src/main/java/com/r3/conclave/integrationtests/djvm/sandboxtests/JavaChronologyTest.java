package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
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
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();

                    Function<? super String, String[]> chronologyTask = taskFactory.create(GetChronologyNames.class);
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.JavaChronologyTest.AvailableChronologiesTest().assertResult(testResult);
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

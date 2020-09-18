package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaPackageTest {
    public static class FetchingPackageTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Function<String, String> fetchPackage = taskFactory.create(FetchPackage.class);
                    String result = fetchPackage.apply("java.lang");
                    assertThat(result).isNull();
                    output.set(result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeBoolean(output == null).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.JavaPackageTest.FetchingPackageTest().assertResult(testResult);
        }
    }

    public static class FetchPackage implements Function<String, String> {
        @Override
        public String apply(String packageName) {
            Package pkg = Package.getPackage(packageName);
            return (pkg == null) ? null : pkg.getName();
        }
    }

    public static class FetchingAllPackagesTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Function<Object, String[]> fetchAllPackages = taskFactory.create(FetchAllPackages.class);
                    String[] result = fetchAllPackages.apply(null);
                    assertThat(result).isEmpty();
                    output.set(result);
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.JavaPackageTest.FetchingAllPackagesTest().assertResult(testResult);
        }
    }

    public static class FetchAllPackages implements Function<Object, String[]> {
        @Override
        public String[] apply(Object input) {
            return Arrays.stream(Package.getPackages()).map(Package::getName).toArray(String[]::new);
        }
    }
}

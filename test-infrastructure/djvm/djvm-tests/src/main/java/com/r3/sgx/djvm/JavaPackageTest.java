package com.r3.sgx.djvm;

import com.r3.sgx.djvm.util.SerializationUtils;
import com.r3.sgx.test.EnclaveJvmTest;
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
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();
                    Function<String, String> fetchPackage = typedTaskFor(ctx.getClassLoader(), taskFactory, FetchPackage.class);
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
            new com.r3.sgx.djvm.asserters.JavaPackageTest.FetchingPackageTest().assertResult(testResult);
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
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();
                    Function<Object, String[]> fetchAllPackages = typedTaskFor(ctx.getClassLoader(), taskFactory, FetchAllPackages.class);
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
            new com.r3.sgx.djvm.asserters.JavaPackageTest.FetchingAllPackagesTest().assertResult(testResult);
        }
    }

    public static class FetchAllPackages implements Function<Object, String[]> {
        @Override
        public String[] apply(Object input) {
            return Arrays.stream(Package.getPackages()).map(Package::getName).toArray(String[]::new);
        }
    }
}

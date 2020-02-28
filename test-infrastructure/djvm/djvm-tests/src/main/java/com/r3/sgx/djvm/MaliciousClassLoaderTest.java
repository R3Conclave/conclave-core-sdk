package com.r3.sgx.djvm;

import com.r3.sgx.djvm.util.SerializationUtils;
import com.r3.sgx.test.EnclaveJvmTest;
import greymalkin.PureEvil;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.SandboxExecutor;
import net.corda.djvm.rewiring.SandboxClassLoader;
import net.corda.djvm.rules.RuleViolationError;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.r3.sgx.djvm.util.Utilities.throwRuleViolationError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class MaliciousClassLoaderTest {

    public static class TestWithAnEvilClassLoaderEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable throwable = catchThrowable(() -> WithJava.run(executor, ActOfEvil.class, PureEvil.class.getName()));
                assertThat(throwable)
                        .isInstanceOf(NoSuchMethodError.class)
                        .hasMessageContaining("currentTimeMillis")
                        .hasNoCause();
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
            new com.r3.sgx.djvm.asserters.MaliciousClassLoaderTest.TestWithAnEvilClassLoader().assertResult(testResult);
        }
    }

    public static class ActOfEvil implements Function<String, String> {
        @Override
        public String apply(String className) {
            ClassLoader evilLoader = new ClassLoader() {
                @Override
                public Class<?> loadClass(String className, boolean resolve) {
                    throwRuleViolationError();
                    return null;
                }

                @Override
                protected Class<?> findClass(String className) {
                    throwRuleViolationError();
                    return null;
                }
            };
            try {
                Class<?> evilClass = Class.forName(className, true, evilLoader);
                return evilClass.newInstance().toString();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    public static class TestWithEvilParentClassLoaderEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                Throwable throwable = catchThrowable(() -> WithJava.run(executor, ActOfEvilParent.class, PureEvil.class.getName()));
                assertThat(throwable)
                        .isInstanceOf(RuleViolationError.class)
                        .hasMessage("Disallowed reference to API; java.lang.ClassLoader(ClassLoader)")
                        .hasNoCause();
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
            new com.r3.sgx.djvm.asserters.MaliciousClassLoaderTest.TestWithEvilParentClassLoader().assertResult(testResult);
        }
    }

    public static class ActOfEvilParent implements Function<String, String> {
        @Override
        public String apply(String className) {
            ClassLoader evilLoader = new ClassLoader(null) {
                @Override
                public Class<?> loadClass(String className, boolean resolve) {
                    throwRuleViolationError();
                    return null;
                }

                @Override
                protected Class<?> findClass(String className) {
                    throwRuleViolationError();
                    return null;
                }
            };
            try {
                Class<?> evilClass = Class.forName(className, true, evilLoader);
                return evilClass.newInstance().toString();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    public static class TestAccessingParentClassLoaderEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();
                    Function<String, ClassLoader> getParentClassLoader = typedTaskFor(ctx.getClassLoader(), taskFactory, GetParentClassLoader.class);
                    ClassLoader result = getParentClassLoader.apply("");
                    assertThat(result)
                            .isExactlyInstanceOf(SandboxClassLoader.class)
                            .extracting(ClassLoader::getParent)
                            .isExactlyInstanceOf(SandboxClassLoader.class)
                            .isEqualTo(ctx.getClassLoader().getParent());
                    output.set(result.getClass().getCanonicalName());
                } catch(Exception e) {
                    throw new RuntimeException(e);
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
            new com.r3.sgx.djvm.asserters.MaliciousClassLoaderTest.TestAccessingParentClassLoader().assertResult(testResult);
        }
    }

    public static class GetParentClassLoader implements Function<String, ClassLoader> {
        @Override
        public ClassLoader apply(String input) {
            ClassLoader parent = ClassLoader.getSystemClassLoader();

            // In theory, this will iterate up the ClassLoader chain
            // until it locates the DJVM's application ClassLoader.
            while (parent.getClass().getClassLoader() != null && parent.getParent() != null) {
                parent = parent.getParent();
            }
            return parent;
        }
    }

    public static class TestClassLoaderForWhitelistedClassEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                SandboxExecutor<String, ClassLoader> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ClassLoader result = WithJava.run(executor, GetWhitelistedClassLoader.class, "").getResult();
                assertThat(result)
                        .isExactlyInstanceOf(SandboxClassLoader.class);
                output.set(result.getClass().getCanonicalName());
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
            new com.r3.sgx.djvm.asserters.MaliciousClassLoaderTest.TestClassLoaderForWhitelistedClass().assertResult(testResult);
        }
    }

    public static class GetWhitelistedClassLoader implements Function<String, ClassLoader> {
        @Override
        public ClassLoader apply(String input) {
            // A whitelisted class belongs to the application classloader.
            return ClassLoader.class.getClassLoader();
        }
    }
}

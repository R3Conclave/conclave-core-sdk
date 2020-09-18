package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import greymalkin.PureEvil;
import net.corda.djvm.TypedTaskFactory;
import net.corda.djvm.rewiring.SandboxClassLoader;
import net.corda.djvm.rules.RuleViolationError;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.r3.conclave.integrationtests.djvm.sandboxtests.util.Utilities.throwRuleViolationError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class MaliciousClassLoaderTest {

    public static class TestWithAnEvilClassLoaderEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Throwable ex = assertThrows(NoSuchMethodError.class, () -> WithJava.run(taskFactory, ActOfEvil.class, PureEvil.class.getName()));
                    assertThat(ex)
                            .isInstanceOf(NoSuchMethodError.class)
                            .hasMessageContaining("currentTimeMillis")
                            .hasNoCause();
                    output.set(ex.getMessage());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.MaliciousClassLoaderTest.TestWithAnEvilClassLoader().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Throwable ex = assertThrows(RuleViolationError.class, () -> WithJava.run(taskFactory, ActOfEvilParent.class, PureEvil.class.getName()));
                    assertThat(ex)
                            .isInstanceOf(RuleViolationError.class)
                            .hasMessage("Disallowed reference to API; java.lang.ClassLoader(ClassLoader)")
                            .hasNoCause();
                    output.set(ex.getMessage());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.MaliciousClassLoaderTest.TestWithEvilParentClassLoader().assertResult(testResult);
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
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    ClassLoader result = WithJava.run(taskFactory, GetParentClassLoader.class, "");
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.MaliciousClassLoaderTest.TestAccessingParentClassLoader().assertResult(testResult);
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
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    ClassLoader result = WithJava.run(taskFactory, GetWhitelistedClassLoader.class, "");
                    assertThat(result)
                            .isExactlyInstanceOf(SandboxClassLoader.class);
                    output.set(result.getClass().getCanonicalName());
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.MaliciousClassLoaderTest.TestClassLoaderForWhitelistedClass().assertResult(testResult);
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

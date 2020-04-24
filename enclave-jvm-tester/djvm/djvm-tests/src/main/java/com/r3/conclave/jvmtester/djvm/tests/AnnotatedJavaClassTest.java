package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import com.r3.conclave.jvmtester.djvm.tests.util.Log;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation;
import com.r3.conclave.jvmtester.djvm.testsauxiliary.ReadJavaAnnotation;
import com.r3.conclave.jvmtester.djvm.testsauxiliary.UserJavaData;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedJavaClassTest {
    public static class SandboxAnnotationTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            assertThat(UserJavaData.class.getAnnotation(JavaAnnotation.class)).isNotNull();

            try {
                sandbox(emptySet(), singleton("com.r3.conclave.jvmtester.djvm.testsauxiliary.*"), ctx -> {
                    try {
                        Class<?> sandboxClass = loadClass(ctx, UserJavaData.class.getName()).getType();
                        @SuppressWarnings("unchecked")
                        Class<? extends Annotation> sandboxAnnotation
                                = (Class<? extends Annotation>) loadClass(ctx, JavaAnnotation.class.getName()).getType();
                        Annotation annotationValue = sandboxClass.getAnnotation(sandboxAnnotation);
                        assertThat(annotationValue).isNotNull();
                        assertThat(annotationValue.toString())
                                .matches("^\\Q@sandbox.com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation(value=\\E\"?Hello Java!\"?\\)$");
                        output.set(annotationValue);
                    } catch (Throwable throwable) {
                        output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                    }
                    return null;
                });
            } catch (Throwable t) {
                output.set(Log.recursiveStackTrace(t, this.getClass().getCanonicalName()));
            }
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output.toString()).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.AnnotatedJavaClassTest.SandboxAnnotationTest().assertResult(testResult);
        }
    }

    public static class AnnotationInsideSandboxTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(emptySet(), singleton("com.r3.conclave.jvmtester.djvm.testsauxiliary.*"), ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, ReadJavaAnnotation.class, null);

                    assertThat(result)
                            .matches("^\\Q@sandbox.com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation(value=\\E\"?Hello Java!\"?\\)$");
                    output.set(result);
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
            new com.r3.conclave.jvmtester.djvm.asserters.AnnotatedJavaClassTest.AnnotationInsideSandboxTest().assertResult(testResult);
        }
    }

    public static class ReflectionCanFetchAllSandboxedAnnotationsTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(emptySet(), singleton("com.r3.conclave.jvmtester.djvm.**"), ctx -> {
                try {
                    Class<?> sandboxClass = loadClass(ctx, UserJavaData.class.getName()).getType();
                    Annotation[] annotations = sandboxClass.getAnnotations();
                    List<String> names = Arrays.stream(annotations)
                            .map(ann -> ann.annotationType().getName())
                            .collect(toList());
                    assertThat(names).containsExactlyInAnyOrder(
                            "sandbox.com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation"
                    );
                    output.set(names);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringList(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.AnnotatedJavaClassTest.ReflectionCanFetchAllSandboxedAnnotationsTest().assertResult(testResult);
        }
    }

    public static class ReflectionCanFetchAllMetaAnnotationsTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    @SuppressWarnings("unchecked")
                    Class<? extends Annotation> sandboxAnnotation
                            = (Class<? extends Annotation>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation").getType();
                    Annotation[] annotations = sandboxAnnotation.getAnnotations();
                    List<String> names = Arrays.stream(annotations)
                            .map(ann -> ann.annotationType().getName())
                            .collect(toList());
                    assertThat(names).containsExactlyInAnyOrder(
                            "java.lang.annotation.Retention",
                            "java.lang.annotation.Target"
                    );
                    output.set(names);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringList(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.AnnotatedJavaClassTest.ReflectionCanFetchAllMetaAnnotationsTest().assertResult(testResult);
        }
    }

}

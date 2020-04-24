package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.Log;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class IterableTest {
    public static class CreateEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    Iterable<String> stringIterable = WithJava.run(taskFactory, com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.IterableTest.Create.class, null);

                    Iterator<?> iterator = stringIterable.iterator();
                    String result = iterator.getClass().getCanonicalName();
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
            new com.r3.conclave.jvmtester.djvm.asserters.IterableTest.Create().assertResult(testResult);
        }
    }
}

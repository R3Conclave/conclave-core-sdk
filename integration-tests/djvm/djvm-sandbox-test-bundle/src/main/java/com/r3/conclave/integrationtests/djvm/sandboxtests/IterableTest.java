package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.Log;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.Log;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
                    Iterable<String> stringIterable = WithJava.run(taskFactory, CreateIterable.class, null);

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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.IterableTest.Create().assertResult(testResult);
        }
    }

    public static class CreateIterable implements Function<Object, Iterable<String>> {
        @Override
        public Iterable<String> apply(Object o) {
            return new ArrayList<>();
        }
    }
}

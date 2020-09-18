package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.rewiring.SandboxClassLoader;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class SandboxExecutorJavaTest {

    public static class TestTransactionEnclaveTest extends DJVMBase implements EnclaveJvmTest {
        private static final int TX_ID = 101;


        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    SandboxClassLoader classLoader = ctx.getClassLoader();
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = classLoader.createRawTaskFactory();
                    Function<? super Object, ?> verifyTask = taskFactory.compose(classLoader.createSandboxFunction()).apply(ContractWrapper.class);

                    Class<?> sandboxClass = ctx.getClassLoader().toSandboxClass(Transaction.class);
                    Object sandboxTx = sandboxClass.getDeclaredConstructor(Integer.TYPE).newInstance(TX_ID);

                    Throwable throwable = catchThrowableOfType(() -> verifyTask.apply(sandboxTx), IllegalArgumentException.class);
                    assertThat(throwable).hasMessageContaining("Contract constraint violated: txId=" + TX_ID);
                    output.set(throwable.getMessage());
                } catch (Throwable t) {
                    throw new RuntimeException(t);
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
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxExecutorJavaTest.TestTransaction().assertResult(testResult);
        }
    }

    public interface Contract {
        @SuppressWarnings("unused")
        void verify(Transaction tx);
    }

    public static class ContractImplementation implements Contract {
        @Override
        public void verify(@NotNull Transaction tx) {
            throw new IllegalArgumentException("Contract constraint violated: txId=" + tx.getId());
        }
    }

    public static class ContractWrapper implements Function<Transaction, Void> {
        @Override
        public Void apply(Transaction input) {
            new ContractImplementation().verify(input);
            return null;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class Transaction {
        private final int id;

        public Transaction(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }
}

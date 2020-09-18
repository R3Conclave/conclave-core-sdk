package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.google.protobuf.InvalidProtocolBufferException;
import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.base.TestSerializable;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestCurrencyParameter;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestCurrencyParameterList;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestCurrencyParameter;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestCurrencyParameterList;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Currency;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class SandboxCurrencyTest {

    public static class TestCurrenciesEnclaveTest extends DJVMBase implements EnclaveJvmTest, TestSerializable {
        TestCurrencyParameterList INPUT = TestCurrencyParameterList.newBuilder()
                .addValues(TestCurrencyParameter.newBuilder()
                        .setCurrencyCode("GBP")
                        .setDisplayName("British Pound Sterling")
                        .setSymbol("GBP")
                        .setFractionDigits(2).build())
                .addValues(TestCurrencyParameter.newBuilder()
                        .setCurrencyCode("EUR")
                        .setDisplayName("Euro")
                        .setSymbol("EUR")
                        .setFractionDigits(2).build())
                .addValues(TestCurrencyParameter.newBuilder()
                        .setCurrencyCode("USD")
                        .setDisplayName("US Dollar")
                        .setSymbol("USD")
                        .setFractionDigits(2).build())
                .build();

        @Override
        public byte[] getTestInput() {
            return INPUT.toByteArray();
        }

        @Override
        public Object apply(Object optionalInput) {
            AtomicReference<Object> output = new AtomicReference<>();
            TestCurrencyParameterList inputs = (TestCurrencyParameterList) optionalInput;
            TestCurrencyParameterList.Builder outputListBuilder = TestCurrencyParameterList.newBuilder();
            sandbox(ctx -> {
                try {
                    for (TestCurrencyParameter input : inputs.getValuesList()) {
                        TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                        Object[] result = WithJava.run(taskFactory, GetCurrency.class, input.getCurrencyCode());
                        assertThat(result).isEqualTo(new Object[]{input.getDisplayName(), input.getSymbol(), input.getFractionDigits()});
                        TestCurrencyParameter outputResult = TestCurrencyParameter.newBuilder()
                                .setDisplayName((String) result[0])
                                .setSymbol((String) result[1])
                                .setFractionDigits((int) result[2])
                                .build();
                        outputListBuilder.addValues(outputResult);
                    }
                    output.set(outputListBuilder.build().toByteArray());
                } catch (Exception e) {
                    fail(e);
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeByteArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.integrationtests.djvm.sandboxtests.asserters.SandboxCurrencyTest.TestCurrencies().assertResult(testResult);
        }

        @Nullable
        @Override
        public Object deserializeTestInput(@NotNull byte[] data) throws InvalidProtocolBufferException   {
            return TestCurrencyParameterList.parseFrom(data);
        }
    }

    public static class GetCurrency implements Function<String, Object[]> {
        @Override
        public Object[] apply(String currencyCode) {
            Currency currency = Currency.getInstance(currencyCode);
            return new Object[] {
                    currency.getDisplayName(),
                    currency.getSymbol(),
                    currency.getDefaultFractionDigits()
            };
        }
    }
}

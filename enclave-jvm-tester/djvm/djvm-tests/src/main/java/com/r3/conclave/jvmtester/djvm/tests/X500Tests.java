package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;
import sun.security.x509.AVA;
import sun.security.x509.X500Name;

import javax.security.auth.x500.X500Principal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class X500Tests {

    public static class TestCreateX500PrincipalEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, CreateX500Principal.class, "CN=Example,O=Corda,C=GB");
                    assertThat(result).isEqualTo("cn=example,o=corda,c=gb");
                    output.set(result);
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
            new com.r3.conclave.jvmtester.djvm.asserters.X500Tests.TestCreateX500Principal().assertResult(testResult);
        }
    }

    public static class CreateX500Principal implements Function<String, String> {
        public String apply(String input) {
            X500Principal principal = new X500Principal(input);
            return principal.getName(X500Principal.CANONICAL);
        }
    }

    public static class TestX500PrincipalToX500NameEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String[] result = WithJava.run(taskFactory, X500PrincipalToX500Name.class, "CN=Example,O=Corda,C=GB");
                    assertThat(result).isEqualTo(new String[] {
                            "c=gb", "cn=example", "o=corda"
                    });
                    output.set(result);
                } catch (Exception e) {
                    fail(e);
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
            new com.r3.conclave.jvmtester.djvm.asserters.X500Tests.TestX500PrincipalToX500Name().assertResult(testResult);
        }
    }

    public static class X500PrincipalToX500Name implements Function<String, String[]> {
        public String[] apply(String input) {
            X500Name name = X500Name.asX500Name(new X500Principal(input));
            return name.allAvas().stream().map(AVA::toRFC2253CanonicalString).sorted().toArray(String[]::new);
        }
    }

    public static class TestX500NameToX500PrincipalEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String result = WithJava.run(taskFactory, X500NameToX500Principal.class, "CN=Example,O=Corda,C=GB");
                    assertThat(result).isEqualTo("cn=example,o=corda,c=gb");
                    output.set(result);
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
            new com.r3.conclave.jvmtester.djvm.asserters.X500Tests.TestX500NameToX500Principal().assertResult(testResult);
        }
    }

    public static class X500NameToX500Principal implements Function<String, String> {
        public String apply(String input) {
            X500Principal principal = X500Name.asX500Name(new X500Principal(input)).asX500Principal();
            return principal.getName(X500Principal.CANONICAL);
        }
    }
}

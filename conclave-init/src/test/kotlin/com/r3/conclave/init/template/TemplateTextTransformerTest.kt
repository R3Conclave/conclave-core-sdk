package com.r3.conclave.init.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TemplateTextTransformerTest {
    @Test
    fun `transform kotlin`() {
        val templateFileContents = """
        package com.r3.conclave.template.enclave

        import com.r3.conclave.enclave.Enclave
        import org.junit.jupiter.api.Assertions.assertEquals

        class TemplateEnclaveTest : Enclave() {
            @Test
            fun `first test`() {
                val mockHost = EnclaveHost.load("com.r3.conclave.template.TemplateEnclave")

                assertTrue(true)
            }
        }
        """.trimIndent()

        val expected = """
        package com.megacorp.enclave

        import com.r3.conclave.enclave.Enclave
        import org.junit.jupiter.api.Assertions.assertEquals

        class MegaEnclaveTest : Enclave() {
            @Test
            fun `first test`() {
                val mockHost = EnclaveHost.load("com.megacorp.MegaEnclave")

                assertTrue(true)
            }
        }
        """.trimIndent()

        val transformed = TemplateTextTransformer(
            JavaPackage("com.megacorp"),
            JavaClass("MegaEnclave")
        ).transform(templateFileContents)
        assertEquals(expected, transformed)

    }

    @Test
    fun `transform java`() {
        val templateFileContents = """
        package com.r3.conclave.template.enclave;

        import com.r3.conclave.enclave.Enclave;

        import java.io.IOException;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        class TemplateEnclaveTest {
            private static ArrayList<MailCommand> mailCommands = new ArrayList<>();

            @Test
            void firstTest() throws EnclaveLoadException, IOException {
                // Start the enclave
                EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.template.enclave.TemplateEnclave");
                mockHost.start(new AttestationParameters.DCAP(), null, null, (commands) -> mailCommands.addAll(commands));
                assertTrue(true)
            }
        }
        """.trimIndent()

        val expected = """
        package com.megacorp.enclave;

        import com.r3.conclave.enclave.Enclave;

        import java.io.IOException;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        class MegaEnclaveTest {
            private static ArrayList<MailCommand> mailCommands = new ArrayList<>();

            @Test
            void firstTest() throws EnclaveLoadException, IOException {
                // Start the enclave
                EnclaveHost mockHost = EnclaveHost.load("com.megacorp.enclave.MegaEnclave");
                mockHost.start(new AttestationParameters.DCAP(), null, null, (commands) -> mailCommands.addAll(commands));
                assertTrue(true)
            }
        }
        """.trimIndent()

        val transformed = TemplateTextTransformer(
            JavaPackage("com.megacorp"),
            JavaClass("MegaEnclave")
        ).transform(templateFileContents)
        assertEquals(expected, transformed)
    }

    @Test
    fun `transform gradle properties`() {
        val templateFileContents = """
        # Dependency versions
        jupiterVersion=5.6.0
        """.trimIndent()

        val expected = """
        # Dependency versions
        jupiterVersion=5.6.0
        """.trimIndent()

        val transformed = TemplateTextTransformer(
            JavaPackage("com.megacorp"),
            JavaClass("MegaEnclave")
        ).transform(templateFileContents)
        assertEquals(expected, transformed)
    }
}
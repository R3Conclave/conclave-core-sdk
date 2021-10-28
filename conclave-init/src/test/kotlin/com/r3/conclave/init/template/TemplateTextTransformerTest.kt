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

        class TemplateEnclaveKotlinTest : Enclave() {
            @Test
            fun `first test`() {
                val mockHost = EnclaveHost.load("com.r3.conclave.template.TemplateEnclaveKotlin")

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
            JavaClass("TemplateEnclaveKotlin"),
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

        class TemplateEnclaveJavaTest {
            private static ArrayList<MailCommand> mailCommands = new ArrayList<>();

            @Test
            void firstTest() throws EnclaveLoadException, IOException {
                // Start the enclave
                EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.template.enclave.TemplateEnclaveJava");
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
            JavaClass("TemplateEnclaveJava"),
            JavaClass("MegaEnclave")
        ).transform(templateFileContents)
        assertEquals(expected, transformed)

    }
}
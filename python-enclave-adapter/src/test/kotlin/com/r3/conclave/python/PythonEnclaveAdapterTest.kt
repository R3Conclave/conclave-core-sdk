package com.r3.conclave.python

import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.internal.createMockHost
import jep.JepConfig
import jep.JepException
import jep.SharedInterpreter
import jep.python.PyObject
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

class PythonEnclaveAdapterTest {
    companion object {
        private val enclaveOutput = ByteArrayOutputStream()

        @JvmStatic
        @BeforeAll
        fun redirectEnclaveOutput() {
            SharedInterpreter.setConfig(JepConfig().apply {
                redirectStdout(enclaveOutput)
            })
        }
    }

    @field:TempDir
    lateinit var tempDir: Path

    private val enclaveHost = createMockHost(PythonEnclaveAdapter::class.java)
    private val postedMail = ArrayList<MailCommand.PostMail>()

    @AfterEach
    fun close() {
        enclaveHost.close()
        println(">> Begin enclave output")
        enclaveOutput.writeTo(System.out)
        println(">> End enclave output")
        enclaveOutput.reset()
    }

    @Test
    fun `on_enclave_startup() python function called at startup`() {
        loadEnclave("""
            on_startup_called = False
            
            def on_enclave_startup():
                global on_startup_called
                on_startup_called = True
        """.trimIndent())
        assertThat(pythonValue<Boolean>("on_startup_called")).isEqualTo(true)
    }

    @Test
    fun `on_enclave_startup() is optional`() {
        loadEnclave("""
            def on_enclave_shutdown():
                pass
        """.trimIndent())
    }

    @Test
    fun `on_enclave_shutdown() python function called on close`() {
        loadEnclave("""
            def on_enclave_shutdown():
                print("*** on_enclave_shutdown() executed ***")
        """.trimIndent())
        // It's difficult to test the shutdown call because it also closes the Jep interpreter, and we use that to
        // examine the enclave. So asserting on the stdout output is the best we can do.
        assertThat(enclaveOutput.toString()).doesNotContain("*** on_enclave_shutdown() executed ***")
        enclaveHost.close()
        assertThat(enclaveOutput.toString()).contains("*** on_enclave_shutdown() executed ***")
    }

    @Test
    fun `on_enclave_shutdown() is optional`() {
        loadEnclave("""
            def on_enclave_startup():
                pass
        """.trimIndent())
    }

    @Test
    fun `UnsupportedOperationException is thrown if receive_from_untrusted_host() is not defined`() {
        loadEnclave("")
        assertThatThrownBy {
            enclaveHost.callEnclave("hello world".toByteArray())
        }.isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessageContaining("receive_from_untrusted_host")
    }

    @Test
    fun `data sent to receive_from_untrusted_host() python function is of type 'bytes'`() {
        loadEnclave("""
            parameter = None
            def receive_from_untrusted_host(from_host):
                global parameter
                parameter = TypeAndValue(from_host)
        """.trimIndent())
        // Use all signed values to make sure they get converted properly in python.
        val allByteValues = ByteArray(256) { it.toByte() }
        enclaveHost.callEnclave(allByteValues)
        inspectPythonEnclave {
            assertPythonBytes("parameter", allByteValues)
        }
    }

    @Test
    fun `return value from receive_from_untrusted_host() is passed through`() {
        loadEnclave("""
            def receive_from_untrusted_host(from_host):
                return bytes("hello world!", "utf-8")
        """.trimIndent())
        val returnValue = enclaveHost.callEnclave(byteArrayOf())
        assertThat(returnValue).isEqualTo("hello world!".toByteArray())
    }

    @Test
    fun `no return value from receive_from_untrusted_host() is treated as null`() {
        loadEnclave("""
            def receive_from_untrusted_host(bytes):
                print("hello there!")
        """.trimIndent())
        val returnValue = enclaveHost.callEnclave("hello world".toByteArray())
        assertThat(returnValue).isNull()
    }

    @Test
    fun `non-bytearray return value from receive_from_untrusted_host() is not allowed`() {
        loadEnclave("""
            def receive_from_untrusted_host(bytes):
                return "hello there!"
        """.trimIndent())
        assertThatThrownBy {
            enclaveHost.callEnclave(byteArrayOf())
        }.isInstanceOf(ClassCastException::class.java)
            .hasMessageContaining("receive_from_untrusted_host")
    }

    @Test
    fun `python enclave able to hold state`() {
        loadEnclave("""
            previous_value = None
            
            def receive_from_untrusted_host(from_host):
                global previous_value
                value = previous_value
                previous_value = from_host
                return value
        """.trimIndent())

        assertThat(enclaveHost.callEnclave("1".toByteArray())).isNull()
        assertThat(enclaveHost.callEnclave("2".toByteArray())).isEqualTo("1".toByteArray())
        assertThat(enclaveHost.callEnclave("3".toByteArray())).isEqualTo("2".toByteArray())
    }

    @Test
    fun `error in previous call does not affect subsequent one`() {
        loadEnclave("""
            def receive_from_untrusted_host(from_host):
                if (from_host.decode("utf-8") == "divide by zero"):
                    f = 1 / 0
                else:
                    return bytes("no exception", "utf-8")
        """.trimIndent())

        assertThatExceptionOfType(JepException::class.java)
            .isThrownBy { enclaveHost.callEnclave("divide by zero".toByteArray()) }
            .withMessageContaining("division by zero")

        assertThat(enclaveHost.callEnclave(byteArrayOf())).isEqualTo("no exception".toByteArray())
    }

    @Test
    fun `UnsupportedOperationException is thrown if receive_enclave_mail() is not defined`() {
        loadEnclave("")
        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val mailBytes = postOffice.encryptMail("123456789".toByteArray())
        assertThatThrownBy {
            enclaveHost.deliverMail(mailBytes, null)
        }.isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessageContaining("receive_enclave_mail")
    }

    @Test
    fun `mail body is converted to 'bytes' inside python EnclaveMail`() {
        loadEnclave("""
            body = None
            def receive_enclave_mail(mail):
                global body
                body = TypeAndValue(mail.body)
        """.trimIndent())

        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val mailBytes = postOffice.encryptMail("123456789".toByteArray())
        enclaveHost.deliverMail(mailBytes, null)

        inspectPythonEnclave {
            assertPythonBytes("body", "123456789".toByteArray())
        }
    }

    @Test
    fun `mail authenticated sender encoded form is converted to 'bytes' inside python EnclaveMail`() {
        loadEnclave("""
            authenticated_sender = None
            def receive_enclave_mail(mail):
                global authenticated_sender
                authenticated_sender = TypeAndValue(mail.authenticated_sender)
        """.trimIndent())

        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val mailBytes = postOffice.encryptMail(byteArrayOf())
        enclaveHost.deliverMail(mailBytes, null)

        inspectPythonEnclave {
            assertPythonBytes("authenticated_sender", postOffice.senderPublicKey.encoded)
        }
    }

    @Test
    fun `mail envelope is converted to 'bytes' inside python EnclaveMail`() {
        loadEnclave("""
            envelope = None
            def receive_enclave_mail(mail):
                global envelope
                envelope = TypeAndValue(mail.envelope)
        """.trimIndent())

        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()

        val withEnvelope = postOffice.encryptMail(byteArrayOf(), "1234".toByteArray())
        enclaveHost.deliverMail(withEnvelope, null)
        inspectPythonEnclave {
            assertPythonBytes("envelope", "1234".toByteArray())
        }

        val withoutEnvelope = postOffice.encryptMail(byteArrayOf(), null)
        enclaveHost.deliverMail(withoutEnvelope, null)
        inspectPythonEnclave {
            assertPythonBytes("envelope", null)
        }
    }

    @Test
    fun `return bytes from receive_enclave_mail() is converted to mail response back to the sender key`() {
        loadEnclave("""
            def receive_enclave_mail(mail):
                return mail.body[::-1]
        """.trimIndent())

        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()

        val mailBytesIn = postOffice.encryptMail("1234".toByteArray())
        enclaveHost.deliverMail(mailBytesIn, null)

        val mailBytesOut = postedMail.single().encryptedBytes
        val mailResponse = postOffice.decryptMail(mailBytesOut)
        assertThat(mailResponse.bodyAsBytes).isEqualTo("4321".toByteArray())
    }

    @Test
    fun `no response mail is created if receive_enclave_mail() has no return value`() {
        loadEnclave("""
            def receive_enclave_mail(mail):
                print("receive_mail")
        """.trimIndent())

        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val mailBytesIn = postOffice.encryptMail("1234".toByteArray())
        enclaveHost.deliverMail(mailBytesIn, null)
        assertThat(postedMail).isEmpty()
    }

    @Test
    fun `tuple return value from receive_enclave_mail() is treated as response body and envelope`() {
        loadEnclave("""
            def receive_enclave_mail(mail):
                return ("body".encode(), "envelope".encode())
        """.trimIndent())

        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()

        val mailBytesIn = postOffice.encryptMail(byteArrayOf())
        enclaveHost.deliverMail(mailBytesIn, null)

        val mailBytesOut = postedMail.single().encryptedBytes
        val mailResponse = postOffice.decryptMail(mailBytesOut)
        assertThat(mailResponse.bodyAsBytes).isEqualTo("body".toByteArray())
        assertThat(mailResponse.envelope).isEqualTo("envelope".toByteArray())
    }

    @Test
    fun `enclave_sign() function delegates to Enclave signer()`() {
        loadEnclave("""
            def receive_from_untrusted_host(bytes):
                return enclave_sign(bytes)
        """.trimIndent())

        val data = "hello world".toByteArray()
        val signature = enclaveHost.callEnclave(data)!!

        with(enclaveHost.enclaveInstanceInfo.verifier()) {
            update(data)
            assertTrue(verify(signature))
        }
    }

    private fun loadEnclave(script: String) {
        val scriptFile = tempDir / "test-enclave.py"
        scriptFile.writeText(script)
        mockEnclave.pythonScriptPath = scriptFile
        enclaveHost.start(null, null, null) { commands ->
            postedMail += commands.filterIsInstance<MailCommand.PostMail>()
        }
        inspectPythonEnclave {
            // Load some python code to help in the testing
            exec("""
                class TypeAndValue:
                    def __init__(self, value):
                        self.type = str(type(value))
                        self.value = value
            """.trimIndent())
        }
    }

    private val mockEnclave: PythonEnclaveAdapter get() = enclaveHost.mockEnclave as PythonEnclaveAdapter

    private fun <T> inspectPythonEnclave(block: SharedInterpreter.() -> T): T = mockEnclave.interpreter(block)

    private inline fun <reified T> pythonValue(string: String): T {
        return inspectPythonEnclave { getValue(string, T::class.java) }
    }

    private fun SharedInterpreter.assertPythonBytes(str: String, expected: ByteArray?) {
        val obj = getValue(str, PyObject::class.java)
        if (expected != null) {
            assertThat(obj.getAttr("type")).isEqualTo("<class 'bytes'>")
        }
        assertThat(obj.getAttr("value")).isEqualTo(expected)
    }
}

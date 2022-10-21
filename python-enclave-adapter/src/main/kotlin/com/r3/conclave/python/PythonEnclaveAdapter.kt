package com.r3.conclave.python

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.utilities.internal.copyResource
import com.r3.conclave.utilities.internal.getOrThrow
import jep.JepConfig
import jep.JepException
import jep.MainInterpreter
import jep.SharedInterpreter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.absolutePathString

class PythonEnclaveAdapter : Enclave() {
    companion object {
        private val enclaveScripthPath = Files.createTempFile(null, null)

        init {
            enclaveScripthPath.toFile().deleteOnExit()
            Companion::class.java.copyResource("enclave.py", enclaveScripthPath)
            if (!java.lang.Boolean.getBoolean("disableStdRedirect")) {
                SharedInterpreter.setConfig(JepConfig().apply {
                    redirectStdErr(System.err)
                    redirectStdout(System.out)
                })
            }
        }
    }

    // We use the same single thread for all access to the Python interpreter. You would think that
    // threadSafe = false already does that for us but that's not the case. SharedInterpreter has an odd
    // requirement that only the _same_ thread can access it. threadSafe = false will only guarantee that at most
    // one thread will enter into the enclave, but this could be different threads over time. newSingleThreadExecutor()
    // gives us that control. And since we're now handling the thread safety of the enclave, it's OK to override
    // threadSafe = true and remove the redundant overhead.
    private val singletonThread = Executors.newSingleThreadExecutor()

    override val threadSafe: Boolean get() = true

    private lateinit var pythonInterpreter: SharedInterpreter

    // Ability to specify the script path is for unit testing.
    internal var pythonScriptPath: Path? = null

    override fun onStartup() {
        val script = pythonScriptPath ?: run {
            // TODO When integrated with Gramine the python script path will probably be a known hardcoded path in
            //  Docker image that our Gradle plugin creates.
            throw UnsupportedOperationException("Gramine integration not supported yet")
        }
        //
        pythonInterpreter = singletonThread.submit(Callable {
            SharedInterpreter().apply {
                set("__enclave", this@PythonEnclaveAdapter)
                runScript(enclaveScripthPath.absolutePathString())
                // Load the user's python script which will have the implementation of the enclave. We use duck
                // typing rather than inheritence as that seems to be work better with python.
                runScript(script.absolutePathString())
                exec("""
try:
    on_enclave_startup()
except NameError:
    pass
""")
            }
        }).getOrThrow()
    }

    override fun onShutdown() {
        if (::pythonInterpreter.isInitialized) {
            interpreter {
                exec("""
try:
    on_enclave_shutdown()
except NameError:
    pass
""")
                close()
            }
        }
        singletonThread.shutdownNow()
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        val returnValue = try {
            interpreter {
                set("__bytes", bytes)
                getValue("receive_from_untrusted_host(__convert_jbytes(__bytes))")
            }
        } catch (e: JepException) {
            if (e.message?.contains("name 'receive_from_untrusted_host' is not defined") == true) {
                throw UnsupportedOperationException("Implement receive_from_untrusted_host(bytes) for local host " +
                        "communication")
            } else {
                throw e
            }
        }
        return when (returnValue) {
            is ByteArray -> returnValue
            null -> null
            else -> throw ClassCastException("Invalid return type from receive_from_untrusted_host(bytes) " +
                    "(${returnValue.javaClass.name})")
        }
    }

    override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
        val returnValue = try {
            interpreter {
                set("__mail", mail)
                getValue(
                    "receive_enclave_mail(EnclaveMail(" +
                            "__convert_jbytes(__mail.getBodyAsBytes()), " +
                            "__convert_jbytes(__mail.getAuthenticatedSender().getEncoded()), " +
                            "__convert_jbytes(__mail.getEnvelope())" +
                            "))"
                )
            }
        } catch (e: JepException) {
            if (e.message?.contains("name 'receive_enclave_mail' is not defined") == true) {
                throw UnsupportedOperationException("Implement receive_enclave_mail(mail) to process mail")
            } else {
                throw e
            }
        }

        var body: ByteArray? = null
        var envelope: ByteArray? = null

        if (returnValue is ByteArray) {
            body = returnValue
        } else if (returnValue is List<*> && returnValue.size == 2) {
            body = returnValue[0] as ByteArray
            envelope = returnValue[1] as ByteArray
        }

        if (body != null) {
            // Encrypt the return value as a mail response back to the sender key.
            postMail(postOffice(mail).encryptMail(body, envelope), routingHint)
        } else if (returnValue != null) {
            throw ClassCastException("Invalid return type from receive_enclave_mail(mail) (${returnValue.javaClass.name})")
        }
    }

    internal fun <T> interpreter(block: SharedInterpreter.() -> T): T {
        return singletonThread.submit(Callable { block(pythonInterpreter) }).getOrThrow()
    }

    @Suppress("unused")  // Used by enclave.py
    fun pythonSign(data: ByteArray): ByteArray {
        return with(signer()) {
            update(data)
            sign()
        }
    }
}

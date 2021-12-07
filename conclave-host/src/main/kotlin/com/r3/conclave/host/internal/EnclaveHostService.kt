package com.r3.conclave.host.internal

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand.PostMail
import com.r3.conclave.host.MailCommand.StoreSealedState
import com.r3.conclave.host.kds.KDSConfiguration
import java.nio.file.Path
import java.util.*

/**
 * Abstract class which provides the framework for a host to behave in the manner expected from `EnclaveClient` and
 * `EnclaveTransport`.
 *
 * We may decide that this is always how a host should behave in which this class should be moved to the public API or
 * embedded into [EnclaveHost].
 */
// TODO Mail command transactionality
abstract class EnclaveHostService : AutoCloseable {
    // This thread local holds the first synchronous mail response the enclave makes to the request mail sent to
    // "deliverMail". A synchronous response is one the enclave creates back to the sender. The sender is defined by
    // the routing hint (from the PoV of the host) and so the mail commands callback will look to see if any post mail
    // command is for the same routing hint that was used in "deliverMail". Only the first response can be sent back.
    // If the enclave created multiple mail for the same routing hint then the subsequent ones are available as
    // "asychronous" responses.
    private val synchronousResponse = ThreadLocal<Any>()
    // An asychronous response is primarily one where the enclave produces a mail for a client who is not the sender of
    // the inbound mail. These mail are retrieved by the client by polling for them.
    private val asynchronousResponses = HashMap<String, Queue<ByteArray>>()

    abstract val enclaveHost: EnclaveHost

    fun start(
        attestationParameters: AttestationParameters?,
        sealedState: ByteArray?,
        enclaveFileSystemFile: Path?,
        kdsConfiguration: KDSConfiguration?
    ) {
        enclaveHost.start(attestationParameters, sealedState, enclaveFileSystemFile, kdsConfiguration) { commands ->
            for (command in commands) {
                when (command) {
                    is PostMail -> processPostMail(command.routingHint, command.encryptedBytes)
                    is StoreSealedState -> storeSealedState(command.sealedState)
                }
            }
        }
    }

    abstract fun storeSealedState(sealedState: ByteArray)

    fun deliverMail(
        encryptedMail: ByteArray,
        routingHint: String,
        callback: ((ByteArray) -> ByteArray?)? = null
    ): ByteArray? {
        // Set the current routing hint so that the mail commands callback can know which of the post mail commands
        // is a sychronous response.
        synchronousResponse.set(routingHint)
        try {
            if (callback != null) {
                enclaveHost.deliverMail(encryptedMail, routingHint, callback)
            } else {
                enclaveHost.deliverMail(encryptedMail, routingHint)
            }
            // If the enclave produced a sychronous response then return it, otherwise return null.
            return synchronousResponse.get() as? ByteArray
        } finally {
            synchronousResponse.remove()
        }
    }

    fun pollMail(routingHint: String): ByteArray? {
        synchronized(asynchronousResponses) {
            val queue = asynchronousResponses[routingHint] ?: return null
            val mail = queue.poll()
            if (queue.isEmpty()) {
                // If there are no more mail for the given routing hint then remove the empty mapping.
                asynchronousResponses.remove(routingHint)
            }
            return mail
        }
    }

    private fun processPostMail(routingHint: String?, encryptedMail: ByteArray) {
        checkNotNull(routingHint) { "Null routing hint not supported by this host." }
        if (synchronousResponse.get() == routingHint) {
            synchronousResponse.set(encryptedMail)
        } else {
            synchronized(asynchronousResponses) {
                val queue = asynchronousResponses.computeIfAbsent(routingHint) { LinkedList() }
                queue += encryptedMail
            }
        }
    }

    override fun close() {
        enclaveHost.close()
    }
}

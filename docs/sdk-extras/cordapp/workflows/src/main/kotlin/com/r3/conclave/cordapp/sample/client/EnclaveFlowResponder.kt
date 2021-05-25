package com.r3.conclave.cordapp.sample.client

import co.paralleluniverse.fibers.Suspendable
import com.r3.conclave.cordapp.host.EnclaveHostService
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap

/**
 * A helper class that wraps some boilerplate for flow responders to communicate with the hosted Enclave and respond
 * to the secure flow initiator.
 */
class EnclaveFlowResponder(
    private val flow: FlowLogic<*>,
    private val session: FlowSession,
    private val host: EnclaveHostService
) {
    /**
     * Relay an encrypted message from the secure flow initiator to the hosted Enclave, and forwards back the Enclave's
     * encrypted response to the sender
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun relayMessageToFromEnclave() {
        // Other party sends us an encrypted mail.
        val encryptedMail = session.receive(ByteArray::class.java).unwrap { it }
        // Deliver and wait for the enclave to reply. The flow will suspend until the enclave chooses to deliver a mail
        // to this flow, which might not be immediately.
        val encryptedReply: ByteArray = flow.await(host.deliverAndPickUpMail(flow, encryptedMail))
        // Send back to the other party the encrypted enclave's reply
        session.send(encryptedReply)
    }

    /**
     * Relay an encrypted message from the secure flow initiator to the hosted Enclave
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun relayMessageToEnclave() {
        val encryptedMail = session.receive(ByteArray::class.java).unwrap { it }
        host.deliverMail(encryptedMail)
    }

    /**
     * Relays an Enclave's encrypted response to the secure flow initiator
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun relayMessageFromEnclave() {
        val encryptedReply: ByteArray = flow.await(host.pickUpMail(flow))
        session.send(encryptedReply)
    }
}

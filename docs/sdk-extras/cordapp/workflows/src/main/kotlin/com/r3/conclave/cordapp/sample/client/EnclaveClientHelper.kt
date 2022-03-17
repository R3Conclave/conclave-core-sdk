package com.r3.conclave.cordapp.sample.client

import co.paralleluniverse.fibers.Suspendable
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.InvalidEnclaveException
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.cordapp.host.EnclaveHostService
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

/**
 * A factory class that wraps some boilerplate for setting up secure initiator and receiver flow helpers
 */
object EnclaveClientHelper {
    /**
     * Creates a [EnclaveFlowInitiator] helper class instance that wraps some boilerplate for senders to initiate
     * secure flows and communicate with a given party.
     *
     * @param flow takes a reference to the flow being executed
     * @param receiver the receiving party in the network
     * @param constraint the Enclave constraints to be used to validate the other party's Enclave instance
     * @param anonymous whether the flow initiator should send its identity to the enclave or not
     * @return the instance of the flow initiator helper class
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    @JvmStatic
    @JvmOverloads
    fun initiateFlow(flow: FlowLogic<*>, receiver: Party, constraint: String,
                     anonymous: Boolean = false): EnclaveFlowInitiator {
        val session = flow.initiateFlow(receiver)

        // Read the enclave attestation from the peer.
        val attestation = session.receive(ByteArray::class.java).unwrap { from: ByteArray ->
            EnclaveInstanceInfo.deserialize(from)
        }

        // The key hash below (the hex string after 'S') is the public key version of sample_private_key.pem
        // In a real app you should remove the SEC:INSECURE part, of course.
        try {
            EnclaveConstraint.parse(constraint).check(attestation)
        } catch (e: InvalidEnclaveException) {
            throw FlowException(e)
        }
        val instance = EnclaveFlowInitiator(flow, session, attestation)
        instance.sendIdentityToEnclave(anonymous)

        return instance
    }

    /**
     * Creates a [EnclaveFlowResponder] helper class instance that wraps some boilerplate for receivers to initiate
     * secure flows and respond to a given party.
     * @param flow takes a reference to the flow being executed
     * @param counterPartySession the sender party
     * @param serviceType a class that specializes the [EnclaveHostService] type to define the enclave to be loaded
     * @return the instance of the responder flow responder helper class
     */
    @Suspendable
    @Throws(FlowException::class)
    @JvmStatic
    fun <T : EnclaveHostService> initiateResponderFlow(flow: FlowLogic<*>, counterPartySession: FlowSession,
                                                       serviceType: Class<T>): EnclaveFlowResponder {
        // Start an instance of the enclave hosting service
        val host = flow.serviceHub.cordaService(serviceType)
        // Send the other party the enclave identity (remote attestation) for verification.
        counterPartySession.send(host.attestationBytes)
        val instance = EnclaveFlowResponder(flow, counterPartySession, host)
        // Relay the initial identity message to the enclave and relay the response back
        instance.relayMessageToFromEnclave()
        return instance
    }
}

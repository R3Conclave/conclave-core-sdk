package com.r3.conclave.cordapp.sample.host;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.conclave.cordapp.client.EnclaveClientHelper;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;

import java.nio.charset.StandardCharsets;

/**
 * Request a peer to reverse a string for us inside an enclave.
 */
@InitiatingFlow
@StartableByRPC
public class ReverseFlow extends FlowLogic<String> {
    private final Party receiver;
    private final String message;
    private final String constraint;

    public ReverseFlow(Party receiver, String message, String constraint) {
        this.receiver = receiver;
        this.message = message;
        this.constraint = constraint;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        FlowSession session = initiateFlow(receiver);
        // Creating and starting the helper will receive the remote attestation from the receiver party, and verify it
        // against this constraint.
        EnclaveClientHelper enclave = new EnclaveClientHelper(session, constraint).start();

        // We can now send and receive messages. They'll be encrypted automatically.
        enclave.sendToEnclave(message.getBytes(StandardCharsets.UTF_8));
        return new String(enclave.receiveFromEnclave());
    }
}
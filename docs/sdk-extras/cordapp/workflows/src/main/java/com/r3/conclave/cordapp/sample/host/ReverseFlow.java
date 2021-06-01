package com.r3.conclave.cordapp.sample.host;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.conclave.cordapp.sample.client.EnclaveClientHelper;
import com.r3.conclave.cordapp.sample.client.EnclaveFlowInitiator;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
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
    private final Boolean anonymous;

    public ReverseFlow(Party receiver, String message, String constraint) {
        this(receiver, message, constraint, false);
    }

    public ReverseFlow(Party receiver, String message, String constraint, Boolean anonymous) {
        this.receiver = receiver;
        this.message = message;
        this.constraint = constraint;
        this.anonymous = anonymous;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        EnclaveFlowInitiator session = EnclaveClientHelper.initiateFlow(this, receiver, constraint, anonymous);

        byte[] response = session.sendAndReceive(message.getBytes(StandardCharsets.UTF_8));

        return new String(response);
    }
}

package com.r3.conclave.cordapp.sample.host;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.conclave.cordapp.host.EnclaveHostService;
import net.corda.core.flows.*;

@InitiatedBy(ReverseFlow.class)
public class ReverseFlowResponder extends FlowLogic<Void> {
    private final FlowSession counterpartySession;

    public ReverseFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        final EnclaveHostService enclave = this.getServiceHub().cordaService(ReverseEnclaveService.class);

        // Send the other party the enclave identity (remote attestation) for verification.
        counterpartySession.send(enclave.getAttestationBytes());

        // Receive a mail, send it to the enclave, receive a reply and send it back to the peer.
        relayMessageToFromEnclave(enclave);

        return null;
    }

    @Suspendable
    private void relayMessageToFromEnclave(EnclaveHostService host) throws FlowException {
        // Other party sends us an encrypted mail.
        byte[] encryptedMail = counterpartySession.receive(byte[].class).unwrap(it -> it);
        // Deliver and wait for the enclave to reply. The flow will suspend until the enclave chooses to deliver a mail
        // to this flow, which might not be immediately.
        byte[] encryptedReply = await(host.deliverAndPickUpMail(this, encryptedMail));
        // Send back to the other party the encrypted enclave's reply
        counterpartySession.send(encryptedReply);
    }
}

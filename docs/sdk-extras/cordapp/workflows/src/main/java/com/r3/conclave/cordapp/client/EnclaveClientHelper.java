package com.r3.conclave.cordapp.client;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.conclave.client.EnclaveConstraint;
import com.r3.conclave.client.InvalidEnclaveException;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519KeyPairGenerator;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import org.jetbrains.annotations.NotNull;

import java.security.KeyPair;
import java.util.UUID;

/**
 * A helper class that wraps some boilerplate for communicating with enclaves.
 */
public class EnclaveClientHelper {
    private final FlowSession session;
    private final KeyPair encryptionKey = new Curve25519KeyPairGenerator().generateKeyPair();
    private final String constraint;
    private EnclaveInstanceInfo attestation;
    private MutableMail mail;

    public EnclaveClientHelper(FlowSession session, String constraint) {
        this.session = session;
        this.constraint = constraint;
    }

    @Suspendable
    public EnclaveClientHelper start() throws FlowException {
        if (attestation != null)
            throw new IllegalStateException("An EnclaveClientHelper may not be started more than once.");

        // Read the enclave attestation from the peer.
        // In future, deserialization will be handled more automatically.
        attestation = session.receive(byte[].class).unwrap(EnclaveInstanceInfo::deserialize);

        // The key hash below (the hex string after 'S') is the public key version of sample_private_key.pem
        // In a real app you should remove the SEC:INSECURE part, of course.
        try {
            EnclaveConstraint.parse(constraint).check(attestation);
        } catch (InvalidEnclaveException e) {
            throw new FlowException(e);
        }

        mail = attestation.createMail(new byte[]{});
        mail.setTopic(UUID.randomUUID().toString());
        mail.setSenderPrivateKey(encryptionKey.getPrivate());
        return this;
    }

    @Suspendable
    public void sendToEnclave(byte[] messageBytes) {
        if (attestation == null)
            throw new IllegalStateException("You must call start() first.");
        // Create the encrypted message and send it. We'll use a temporary key pair for now, so the message is
        // effectively unauthenticated so the enclave won't know who sent it. Future samples will show how to
        // integrate with the Corda identity infrastructure.
        //
        // TODO: Port this to the new post office API.
        // TODO: Topic should become just 'default' or be configurable by the user once we adjust topic/seqno semantics.
        mail.setBodyAsBytes(messageBytes);
        session.send(mail.encrypt());
        mail.incrementSequenceNumber();
    }

    @Suspendable
    @NotNull
    public byte[] receiveFromEnclave() throws FlowException {
        if (attestation == null)
            throw new IllegalStateException("You must call start() first.");
        EnclaveMail reply = session.receive(byte[].class).unwrap(it ->
                attestation.decryptMail(it, encryptionKey.getPrivate())
        );
        return reply.getBodyAsBytes();
    }
}

package com.r3.conclave.sample.client;

import com.r3.conclave.client.EnclaveConstraint;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519PrivateKey;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.PostOffice;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

public class Client {
    public static void main(String[] args) throws Exception {
        // This is the client that will upload secrets to the enclave for processing.
        //
        // In this simple hello world app, we just connect to a TCP socket, take the EnclaveInstanceInfo we're sent
        // and transmit an encrypted string. The enclave will reverse it and send it back. You can use this sample
        // as a basis for your own apps.

        if (args.length == 0) {
            System.err.println("Please pass the string to reverse on the command line using --args=\"String to Reverse\"");
            return;
        }
        String toReverse = String.join(" ", args);

        // Connect to the host, it will send us a remote attestation (EnclaveInstanceInfo).
        DataInputStream fromHost;
        DataOutputStream toHost;
        while (true) {
            try {
                System.out.println("Attempting to connect to localhost:9999");
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9999), 5000);
                fromHost = new DataInputStream(socket.getInputStream());
                toHost = new DataOutputStream(socket.getOutputStream());
                break;
            } catch (Exception e) {
                System.err.println("Retrying: " + e.getMessage());
                Thread.sleep(2000);
            }
        }

        byte[] attestationBytes = new byte[fromHost.readInt()];
        fromHost.readFully(attestationBytes);
        EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);

        // Check it's the enclave we expect. This will throw InvalidEnclaveException if not valid.
        System.out.println("Connected to " + attestation);
        // Two distinct signing key hashes can be accepted.
        // Release mode: 360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9
        // Debug mode:   4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
        EnclaveConstraint.parse("S:360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9 "
                + "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE").check(attestation);

        // Now we checked the enclave's identity and are satisfied it's the enclave from this project, we can send mail
        // to it.

        // We will need to provide our own private key whilst encrypting, so the enclave gets our public key and can
        // encrypt a reply. If you already have a long term key then you can use that and the enclave can then
        // use the public key as a form of identity.
        PrivateKey myKey = Curve25519PrivateKey.random();

        // For encrypting mail to the enclave we need to create a PostOffice from the enclave's attestation object.
        // The post office will manage sequence numbers for us if we send more than one mail, which allows the enclave
        // to check that no mail has been dropped or reordered by the host.
        //
        // We use a topic value of "reverse" but any will do in this example. However, mail related to each other and
        // which need to be ordered must use their own topic. Topics are scoped to the sender key and so multiple clients
        // can use the same topic without overlapping with each other.
        //
        // In this example it doesn't matter as we only send one mail with a random key, but in general it is very
        // important to use the same post office instance when encrypting mail with the same topic and private key.
        PostOffice postOffice = attestation.createPostOffice(myKey, "reverse");

        byte[] encryptedMail = postOffice.encryptMail(toReverse.getBytes(StandardCharsets.UTF_8));

        System.out.println("Sending the encrypted mail to the host.");

        toHost.writeInt(encryptedMail.length);
        toHost.write(encryptedMail);

        // Enclave will mail us back.
        byte[] encryptedReply = new byte[fromHost.readInt()];
        System.out.println("Reading reply mail of length " + encryptedReply.length + " bytes.");
        fromHost.readFully(encryptedReply);
        // The same post office will decrypt the response.
        EnclaveMail reply = postOffice.decryptMail(encryptedReply);
        System.out.println("Enclave reversed '" + toReverse + "' and gave us the answer '" + new String(reply.getBodyAsBytes()) + "'");

        toHost.close();
        fromHost.close();
    }
}

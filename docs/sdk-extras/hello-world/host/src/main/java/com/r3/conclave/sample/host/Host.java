package com.r3.conclave.sample.host;

import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.host.AttestationParameters;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.MailCommand;
import com.r3.conclave.host.MockOnlySupportedException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * This class demonstrates how to load an enclave and exchange byte arrays with it.
 */
public class Host {
    public static void main(String[] args) throws EnclaveLoadException, IOException {
        // Report whether the platform supports hardware enclaves.
        //
        // This method will always check the hardware state regardless of whether running in Simulation,
        // Debug or Release mode. If the platform supports hardware enclaves then no exception is thrown.
        // If the platform does not support enclaves or requires enabling, an exception is thrown with the
        // details in the exception message.
        //
        // If the platform supports enabling of enclave support via software then passing true as a parameter
        // to this function will attempt to enable enclave support on the platform. Normally this process
        // will have to be run with root/admin privileges in order for it to be enabled successfully.
        try {
            EnclaveHost.checkPlatformSupportsEnclaves(true);
            System.out.println("This platform supports enclaves in simulation, debug and release mode.");
        } catch (MockOnlySupportedException e) {
            System.out.println("This platform only supports mock enclaves: " + e.getMessage());
            System.exit(1);
        } catch (EnclaveLoadException e) {
            System.out.println("This platform does not support hardware enclaves: " + e.getMessage());
        }

        // Enclaves get interesting when remote clients can talk to them.
        // Let's open a TCP socket and implement a trivial protocol that lets a remote client use it.
        // A real app would use SSL here to protect client/host communications, even though the only
        // data we're sending and receiving here is encrypted to the enclave: better safe than sorry.
        int port = 9999;
        System.out.println("Listening on port " + port + ". Use the client app to send strings for reversal.");
        ServerSocket acceptor = new ServerSocket(port);
        Socket connection = acceptor.accept();

        // Just send the attestation straight to whoever connects. It's signed so that is MITM-safe.
        DataOutputStream output = new DataOutputStream(connection.getOutputStream());

        // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
        // that we defined in our enclave module. This will start the sub-JVM and initialise the class in that, i.e.
        // the ReverseEnclave class is not instantiated in this JVM.
        //
        // We could also shut it down at the end by calling .close() but this isn't necessary if the host is about
        // to quit anyway. Don't load enclaves too often - it's like starting a sub-process and can be somewhat slow
        // because the CPU must hash the contents of the enclave binary.
        EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");

        // Start up the enclave with a callback that will deliver the response. But remember: in a real app that can
        // handle multiple clients, you shouldn't start one enclave per client. That'd be wasteful and won't fit in
        // available encrypted memory. A real app should use the routingHint parameter to select the right connection
        // back to the client, here.
        enclave.start(new AttestationParameters.DCAP(), (commands) -> {
            for (MailCommand command : commands) {
                if (command instanceof MailCommand.PostMail) {
                    try {
                        sendArray(output, ((MailCommand.PostMail) command).getEncryptedBytes());
                    } catch (IOException e) {
                        System.err.println("Failed to send reply to client.");
                        e.printStackTrace();
                    }
                }
            }
        });

        // The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
        final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
        final byte[] attestationBytes = attestation.serialize();

        // It has a useful toString method.
        System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));

        // Now let's send a local message from host to enclave, asking it to reverse a string.
        System.out.println();
        final Charset utf8 = StandardCharsets.UTF_8;
        System.out.println("Reversing Hello World!: " + new String(enclave.callEnclave("Hello World!".getBytes(utf8)), utf8));
        System.out.println();

        sendArray(output, attestationBytes);

        // Now read some mail from the client.
        DataInputStream input = new DataInputStream(connection.getInputStream());
        byte[] mailBytes = new byte[input.readInt()];
        input.readFully(mailBytes);

        // Deliver it. The enclave will give us the encrypted reply in the callback we provided above, which
        // will then send the reply to the client.
        enclave.deliverMail(1, mailBytes, "routingHint");

        // Closing the output stream closes the connection. Different clients will block each other but this
        // is just a hello world sample.
        output.close();
        enclave.close();
    }

    private static void sendArray(DataOutputStream stream, byte[] bytes) throws IOException {
        stream.writeInt(bytes.length);
        stream.write(bytes);
        stream.flush();
    }
}

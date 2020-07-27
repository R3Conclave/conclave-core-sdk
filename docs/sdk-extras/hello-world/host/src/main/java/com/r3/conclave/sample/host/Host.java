package com.r3.conclave.sample.host;

import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.common.EnclaveMode;
import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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
        } catch (EnclaveLoadException e) {
            System.out.println("This platform currently only supports enclaves in simulation mode: " + e.getMessage());
        }

        // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
        // that we defined in our enclave module.
        //
        // We make sure the .close() method is called on the enclave no matter what using a try-with-resources statement.
        //
        // This doesn't actually matter in such a tiny hello world sample, because the enclave will be unloaded by
        // the kernel once we exit like any other resource. It's just here to remind you that an enclave must be
        // explicitly unloaded if you need to reinitialise it for whatever reason, or if you need the memory back.
        //
        // Don't load and unload enclaves too often as it's quite slow.
        try (EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave")) {
            // We need the EPID Service Provider ID (SPID) and attestation key to able to perform remote attestation
            // You can sign-up from Intel's EPID page: https://api.portal.trustedservices.intel.com/EPID-attestation
            // These are not needed if the enclave is in simulation mode (as no actual attestation is done)
            if (enclave.getEnclaveMode() != EnclaveMode.SIMULATION && args.length != 2) {
                throw new IllegalArgumentException("You need to provide the SPID and attestation key as arguments for " +
                        enclave.getEnclaveMode() + " mode.");
            }
            OpaqueBytes spid = enclave.getEnclaveMode() != EnclaveMode.SIMULATION ? OpaqueBytes.parse(args[0]) : null;
            String attestationKey = enclave.getEnclaveMode() != EnclaveMode.SIMULATION ? args[1] : null;

            // Start it up.
            AtomicReference<byte[]> requestToDeliver = new AtomicReference<>();
            enclave.start(spid, attestationKey, new EnclaveHost.MailCallbacks() {
                @Override
                public void postMail(byte[] encryptedBytes, String routingHint) {
                    requestToDeliver.set(encryptedBytes);
                }
            });

            // The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
            final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
            final byte[] attestationBytes = attestation.serialize();
            System.out.println("This attestation requires " + attestationBytes.length + " bytes.");

            // It has a useful toString method.
            System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));

            // Now let's send a local message from host to enclave, asking it to reverse a string.
            System.out.println();
            System.out.println("Reversing Hello World!: " + callEnclave(enclave, "Hello world!"));
            System.out.println();

            // That's not very useful by itself. Enclaves only get interesting when remote clients can talk to them.
            // So now let's open a TCP socket and implement a trivial protocol that lets a remote client use it.
            int port = 9999;
            System.out.println("Listening on port " + port + ". Use the client app to send strings for reversal.");
            ServerSocket acceptor = new ServerSocket(port);
            try {
                Socket connection = acceptor.accept();

                // Just send the attestation straight to whoever connects. It's signed so that's MITM-safe.
                DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                output.writeInt(attestationBytes.length);
                output.write(attestationBytes);
                output.flush();

                // Now read some mail from the client.
                DataInputStream input = new DataInputStream(connection.getInputStream());
                byte[] mailBytes = new byte[input.readInt()];
                input.readFully(mailBytes);

                // Deliver it. The enclave will give us some mail to reply with via the callback we passed in
                // to the start() method.
                enclave.deliverMail(1, mailBytes);
                byte[] toSend = requestToDeliver.get();
                output.writeInt(toSend.length);
                output.write(toSend);

                // Closing the output stream closes the connection. Different clients will block each other but this
                // is just a hello world sample.
                output.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String callEnclave(EnclaveHost enclave, String input) {
        // We'll convert strings to bytes and back.
        final byte[] inputBytes = input.getBytes();

        // Enclaves in general don't have to give bytes back if we send data, but in this sample we know it always
        // will so we can just assert it's non-null here.
        final byte[] outputBytes = Objects.requireNonNull(enclave.callEnclave(inputBytes));
        return new String(outputBytes);
    }

}

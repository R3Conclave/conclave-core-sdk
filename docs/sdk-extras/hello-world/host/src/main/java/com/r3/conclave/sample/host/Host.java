package com.r3.conclave.sample.host;

import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveInstanceInfo;
import com.r3.conclave.host.EnclaveMode;
import com.r3.conclave.host.InvalidEnclaveException;

import java.util.Objects;

/**
 * This class demonstrates how to load an enclave and exchange byte arrays with it.
 */
public class Host {
    public static void main(String[] args) throws InvalidEnclaveException {
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
            OpaqueBytes spid = enclave.getEnclaveMode() != EnclaveMode.SIMULATION ? OpaqueBytes.parse(args[0]) : new OpaqueBytes(new byte[16]);
            String attestationKey = enclave.getEnclaveMode() != EnclaveMode.SIMULATION ? args[1] : "mock-key";
            // Start it up. In future versions this API will take more parameters, which is why it's explicit.
            enclave.start(spid, attestationKey);

            // The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
            final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
            final byte[] attestationBytes = attestation.serialize();
            System.out.println("This attestation requires " + attestationBytes.length + " bytes.");

            // It has a useful toString method.
            System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));

            // !dlrow olleH      :-)
            System.out.println(callEnclave(enclave, "Hello world!"));
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

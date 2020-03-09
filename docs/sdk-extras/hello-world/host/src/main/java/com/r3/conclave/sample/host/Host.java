package com.r3.conclave.sample.host;

import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.InvalidEnclaveException;

import java.util.Objects;

/**
 * This class demonstrates how to load an enclave and exchange byte arrays with it.
 */
public class Host {
    public static void main(String[] args) throws InvalidEnclaveException {
        // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
        // that we defined in our enclave module.
        EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        // Start it up.
        enclave.start();
        try {
            // !dlrow olleH      :-)
            System.out.println(callEnclave(enclave,  "Hello world!"));
        } finally {
            // We make sure the .close() method is called on the enclave no matter what.
            //
            // This doesn't actually matter in such a tiny hello world sample, because the enclave will be unloaded by
            // the kernel once we exit like any other resource. It's just here to remind you that an enclave must be
            // explicitly unloaded if you need to reinitialise it for whatever reason, or if you need the memory back.
            //
            // Don't load and unload enclaves too often as it's quite slow.
            enclave.close();
        }
    }

    public static String callEnclave(EnclaveHost enclave, String input) {
        // We'll convert strings to bytes and back.
        final byte[] inputBytes = input.getBytes();

        // Enclaves in general don't have to give bytes back if we send data, but in our case we know it always
        // will so we can just assert it's non-null here.
        final byte[] outputBytes = Objects.requireNonNull(enclave.callEnclave(inputBytes));
        return new String(outputBytes);
    }
}

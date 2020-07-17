package com.r3.conclave.sample.host;

import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.EnclaveHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This tests the enclave in a simulated hardware environment, thus not needing real hardware only the correct OS.
 * Alternatively it can run the enclave in debug mode on real hardware. See the tutorial for details.
 */
public class HostTest {
    // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
    // that we defined in our enclave module.
    private static EnclaveHost enclave;

    @BeforeAll
    static void startup() throws EnclaveLoadException {
        enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        // Optionally pass in the SPID and attestation key which are required for remote attestation. These can be null
        // if running in simulation mode, but are required in debug/release mode.
        String spid = System.getProperty("spid");
        String attestionKey = System.getProperty("attestation-key");
        enclave.start(spid != null ? OpaqueBytes.parse(spid) : null, attestionKey, null);
    }

    @AfterAll
    static void shutdown() {
        enclave.close();
    }

    @Test
    void reverseNumber() {
        String response = Host.callEnclave(enclave, "123456");
        assertEquals("654321", response);
    }
}

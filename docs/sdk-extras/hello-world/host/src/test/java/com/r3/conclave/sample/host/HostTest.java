package com.r3.conclave.sample.host;

import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.AttestationParameters;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.testing.MockHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This can test the enclave in any mode: on non-Linux platforms it will do a mock test with only pure Java code,
 * nothing SGX specific. On Linux platforms it will try to load the enclave for real, either in simulation mode, or
 * when compiled with the correct -PenclaveMode= flag, in debug or release mode. The test code is the same for all
 * paths except for the line that instantiates the EnclaveHost object.
 */
public class HostTest {
    // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
    // that we defined in our enclave module.
    private static EnclaveHost enclave;

    @BeforeAll
    static void startup() throws EnclaveLoadException {
        try {
            enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        } catch (EnclaveLoadException e) {
            enclave = MockHost.loadMock(com.r3.conclave.sample.enclave.ReverseEnclave.class);
        }
        enclave.start(new AttestationParameters.DCAP(), null);
    }

    @AfterAll
    static void shutdown() {
        enclave.close();
    }

    @Test
    void reverseNumber() {
        String response = new String(enclave.callEnclave("123456".getBytes()));
        assertEquals("654321", response);
    }
}

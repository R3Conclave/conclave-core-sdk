package com.r3.conclave.sample.enclave;

import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the enclave fully in-memory in a mock environment without the web host.
 */
public class MockTest {
    @Test
    void reverseNumber() throws EnclaveLoadException {
        EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        mockHost.start(null, null, null, (commands) -> { });

        /*
         * Directly accessing a field (or a method) of the enclave is only possible in mock mode
         */
        ReverseEnclave reverseEnclave = (ReverseEnclave)mockHost.getMockEnclave();

        assertNull(reverseEnclave.previousResult);

        byte[] response = mockHost.callEnclave("1234".getBytes());
        assertNotNull(response);
        assertEquals("4321", new String(response));

        assertEquals("4321", new String(reverseEnclave.previousResult));
    }
}

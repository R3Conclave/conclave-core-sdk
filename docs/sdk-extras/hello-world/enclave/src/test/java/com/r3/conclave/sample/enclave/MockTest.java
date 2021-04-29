package com.r3.conclave.sample.enclave;

import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.EnclaveHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the enclave fully in-memory in a mock environment.
 */
public class MockTest {
    @Test
    void reverseNumber() throws EnclaveLoadException {
        EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        mockHost.start(null, null);
        ReverseEnclave reverseEnclave = (ReverseEnclave)mockHost.getMockEnclave();

        assertNull(reverseEnclave.previousResult);

        byte[] response = mockHost.callEnclave("1234".getBytes());
        assertNotNull(response);
        assertEquals("4321", new String(response));

        assertEquals("4321", new String(reverseEnclave.previousResult));
    }
}

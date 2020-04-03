package com.r3.conclave.sample.host;

import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.host.EnclaveHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HostTest {
    // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
    // that we defined in our enclave module.
    private static EnclaveHost enclave;

    @BeforeAll
    static void startup() throws InvalidEnclaveException {
        enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        enclave.start(null, null);
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

package com.r3.conclave.sample.host;

import com.r3.conclave.host.AttestationParameters;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test assumes a Linux platform and will try to load the enclave for real, either in simulation mode, or
 * when compiled with the correct -PenclaveMode= flag, in debug or release mode.
 */
@EnabledOnOs(OS.LINUX)
public class NativeTest {
    // We start by loading the enclave using EnclaveHost, and passing the class name of the Enclave subclass
    // that we defined in our enclave module.
    private static EnclaveHost enclave;

    @BeforeAll
    static void startup() throws EnclaveLoadException {
        enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        enclave.start(new AttestationParameters.DCAP(), null);
    }

    @AfterAll
    static void shutdown() {
        if (enclave != null) {
            enclave.close();
        }
    }

    @Test
    void reverseNumber() {
        final byte[] responseBytes = enclave.callEnclave("123456".getBytes());
        String response = new String(Objects.requireNonNull(responseBytes));
        assertEquals("654321", response);
    }
}

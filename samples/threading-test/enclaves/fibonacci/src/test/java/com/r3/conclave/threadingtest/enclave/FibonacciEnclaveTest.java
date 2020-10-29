package com.r3.conclave.threadingtest.enclave;

import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.testing.MockHost;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FibonacciEnclaveTest {
    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "2, 1", "9, 34", "20, 6765", "40, 102334155"})
    void testFibonacci(int term, long expected) throws EnclaveLoadException {
        MockHost<FibonacciEnclave> mockHost = MockHost.loadMock(FibonacciEnclave.class);
        mockHost.start(null, null, null, null);
        mockHost.getEnclave();

        byte[] response = mockHost.callEnclave(ByteBuffer.allocate(4).putInt(term).array());
        assertNotNull(response);
        assertEquals(expected, ByteBuffer.wrap(response).getLong());
    }
}

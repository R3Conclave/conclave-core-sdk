package com.r3.conclave.threadingtest.enclave;

import com.r3.conclave.enclave.Enclave;

import java.nio.ByteBuffer;

/**
 * Computes a given term in the fibonacci sequence.
 */
public class FibonacciEnclave extends Enclave {
    @Override
    protected byte[] receiveFromUntrustedHost(byte[] bytes) {
        int term = ByteBuffer.wrap(bytes).getInt();
        long result = fib(term);
        return ByteBuffer.allocate(8).putLong(result).array();
    }

    public static long fib(int n) {
        switch (n) {
            case (0):
                return 0;
            case (1):
                return 1;
            default:
                return fib(n - 1) + fib(n - 2);
        }
    }
}
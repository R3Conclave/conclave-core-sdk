package com.r3.conclave.sample.enclave;

import com.r3.conclave.common.enclave.EnclaveCall;
import com.r3.conclave.enclave.Enclave;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave implements EnclaveCall {
    // We store the previous result to showcase that the enclave internals can be examined in a mock test.
    byte[] previousResult;

    @Override
    public byte[] invoke(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[bytes.length - 1 - i];
        }
        previousResult = result;
        return result;
    }
}

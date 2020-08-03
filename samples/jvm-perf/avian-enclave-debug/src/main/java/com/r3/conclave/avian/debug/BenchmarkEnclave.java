package com.r3.conclave.avian.debug;

import com.r3.conclave.common.EnclaveCall;
import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.benchmarks.Benchmarks;
import java.nio.charset.StandardCharsets;

/**
 * Enclave implementation for benchmarks.
 */
public class BenchmarkEnclave extends Enclave implements EnclaveCall {
    @Override
    public byte[] invoke(byte[] bytes) {
        // Get the space delimited set of arguments passed to this function
        String args_all = new String(bytes, StandardCharsets.UTF_8);

        // Benchmarks are initiated via a command line. The first parameter is always
        // the benchmark name. Subsequent parameters are benchmark specific.
        try {
            Benchmarks bench = new Benchmarks(args_all);
            bench.run();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }
}

package com.r3.conclave.graalvm.simulation;

import com.r3.conclave.benchmarks.Benchmarks;
import com.r3.conclave.enclave.Enclave;

import java.nio.charset.StandardCharsets;

/**
 * Enclave implementation for benchmarks.
 */
public class BenchmarkEnclave extends Enclave {
    @Override
    protected byte[] receiveFromUntrustedHost(byte[] bytes) {
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

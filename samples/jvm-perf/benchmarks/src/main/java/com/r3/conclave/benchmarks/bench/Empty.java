package com.r3.conclave.benchmarks.bench;

/**
 * This class is an empty benchmark that just provides an empty run() function. The
 * purpose is to allow measurement of the performance of calls into the enclave.
 */
public class Empty implements Benchmark {

    public static String identifier() {
        return "empty";
    }

    @Override
    public void run(String[] args) {

    }
}

package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.pidigits;

/**
 * This class is a wrapper around the Computer Benchmarks Game's implementation
 * of the pidigits benchmark.
 */
public class PiDigits implements Benchmark{

    public static String identifier() {
        return "pidigits";
    }

    @Override
    public void run(String[] args) throws Exception {
        pidigits.main(args);
    }

}

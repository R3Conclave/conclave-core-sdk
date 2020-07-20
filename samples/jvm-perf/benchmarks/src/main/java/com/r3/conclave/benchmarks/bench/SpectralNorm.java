package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.spectralnorm;

/**
 * This class is a wrapper around the Computer Benchmarks Game's implementation
 * of the spectral-norm algorithm benchmark.
 */
public class SpectralNorm implements Benchmark {

    public static String identifier() {
        return "spectralnorm";
    }

    @Override
    public void run(String[] args) {
        spectralnorm.main(args);
    }
}

package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.nbody;

/**
 * This class is a wrapper around the Computer Benchmarks Game's implementation
 * of the n-body benchmark.
 */
public class NBody implements Benchmark {

    public static String identifier() { 
        return "nbody"; 
    }

    @Override
    public void run(String[] args) {
        nbody.main(args);
    }
}


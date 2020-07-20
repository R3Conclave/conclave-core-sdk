package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.binarytrees;

/**
 * This class is a wrapper around the Computer Benchmarks Game's implementation
 * of the binary trees 6 benchmark.
 */
public class BinaryTrees implements Benchmark {
    public static String identifier() {
        return "binarytrees";
    }

    @Override
    public void run(String[] args) throws Exception {
        binarytrees.main(args);
    }
}

package com.r3.conclave.benchmarks.bench;

/**
 * An interface that represents a benchmark operation
 */
public interface Benchmark {

    /**
     * Run the benchmark.
     * @param args  Space separated, benchmark dependent argument list.
     */
    void run(String[] args) throws Exception;
}

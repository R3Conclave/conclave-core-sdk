package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.fasta;

public class Fasta implements Benchmark {

    public static String identifier() {
        return "fasta";
    }

    @Override
    public void run(String[] args) throws Exception {
        fasta.main(args);
    }

}

package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.fannkuchredux;

/**
 * This class is a wrapper around the Computer Benchmarks Game's implementation
 * of the fannkuch-redux benchmark.
 */
public class Fannkuch implements Benchmark {

	public static String identifier() {
		return "fannkuch";
	}

	@Override
	public void run(String[] args) {
		fannkuchredux.main(args);
	}
}

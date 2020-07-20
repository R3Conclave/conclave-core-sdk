package com.r3.conclave.benchmarks.bench;

import com.r3.conclave.benchmarks.bench.benchmarksgame.mandelbrot;

/**
 * This class is a wrapper around the Computer Benchmarks Game's implementation
 * of the mandlebrot benchmark.
 */
public class Mandelbrot implements Benchmark {

	public static String identifier() {
		return "mandelbrot";
	}

    @Override
    public void run(String[] args) throws Exception {
        mandelbrot.main(args);
    }

}

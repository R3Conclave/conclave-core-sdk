package com.r3.conclave.benchmarks;

import com.r3.conclave.benchmarks.bench.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class contains and manages a list of supported benchmarks. It is designed to be
 * used both inside and outside enclaves to measure the relative performance of multiple
 * runtimes.
 */
public class Benchmarks {
    String              benchmark;
    ArrayList<String>   args;

    /**
     * A list of supported benchmarks keyed on their name.
     */
    static Map<String, Benchmark> benchmarks;
    static {
        benchmarks = new HashMap<>();
        benchmarks.put(Empty.identifier(), new Empty());
        benchmarks.put(Fannkuch.identifier(), new Fannkuch());
        benchmarks.put(Mandelbrot.identifier(), new Mandelbrot());
        benchmarks.put(Himeno.identifier(), new Himeno());
        benchmarks.put(NBody.identifier(), new NBody());
        benchmarks.put(SpectralNorm.identifier(), new SpectralNorm());
        benchmarks.put(BinaryTrees.identifier(), new BinaryTrees());
        benchmarks.put(Fasta.identifier(), new Fasta());
        benchmarks.put(PiDigits.identifier(), new PiDigits());
    }

    /**
     * Prepare a benchmark for execution
     * @param configuration Space delimited name of benchmark to run followed by parameters. E.g.:
     *                      "mandelbrot 1000"
     */
    public Benchmarks(String configuration) throws NoSuchMethodException {
        args = new ArrayList<>(Arrays.asList(configuration.split(" ")));

        // The first argument defines which benchmark to run
        if (args.size() == 0) {
            throw new RuntimeException("No test specified.");
        }
        benchmark = args.get(0);
        args.remove(0);

        // Make sure we have a benchmark with the given name
        if (!benchmarks.containsKey(benchmark)) {
            throw new NoSuchMethodException(benchmark);
        }
    }

    /**
     * Run the benchmark
     */
    public void run() {
        try {
            benchmarks.get(benchmark).run(args.toArray(new String[0]));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

package com.r3.conclave.jvmperf;

import com.r3.conclave.common.OpaqueBytes;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.benchmarks.Benchmarks;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * This class defines the benchmarks that will be run. It runs each benchmark three times.
 * Once for the host, once for an Avian enclave and once for a GraalVM native-image enclave.
 */
@State(Scope.Thread)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class EnclaveBenchmark {
    EnclaveHost enclave = null;

    /**
     * Prepare a set of tests. This is run before each entire group of warmups and
     * iterations for each benchmark. It loads both enclaves for consistency even though
     * only one or none are used depending on the test.
     * @throws EnclaveLoadException
     */
    @Setup(Level.Trial)
    public void prepare(ExecutionPlatforms platform) throws EnclaveLoadException {
        if (platform.runtime.equals("avian-simulation"))
            enclave = EnclaveHost.load("com.r3.conclave.avian.simulation.BenchmarkEnclave");
        else if (platform.runtime.equals("avian-debug"))
            enclave = EnclaveHost.load("com.r3.conclave.avian.debug.BenchmarkEnclave");
        else if (platform.runtime.equals("graalvm-simulation"))
            enclave = EnclaveHost.load("com.r3.conclave.graalvm.simulation.BenchmarkEnclave");
        else if (platform.runtime.equals("graalvm-debug"))
            enclave = EnclaveHost.load("com.r3.conclave.graalvm.debug.BenchmarkEnclave");
        if (enclave != null) {
            OpaqueBytes spid = (platform.spid.length() == 0) ? new OpaqueBytes(new byte[16]) : OpaqueBytes.parse(platform.spid);
            enclave.start(spid, platform.attestationKey, null, null);
        }
    }

    /**
     * Teardown the test environment by closing the enclaves. This is run after an entire group
     * of warmups and iterations for a single benchmark.
     */
    @TearDown(Level.Trial)
    public void teardown() {
        if (enclave != null) {
            enclave.close();
            enclave = null;
        }
    }

    /**
     * Run a benchmark on the required platform.
     * @param platform  Class describing the platform to run the benchmark on
     * @param cmdline   Test type and arguments in a space-delimited string
     * @throws NoSuchMethodException
     */
    private void runBenchmark(ExecutionPlatforms platform, String cmdline) throws NoSuchMethodException {
        if (platform.runtime.equals("avian-simulation") ||
            platform.runtime.equals("avian-debug") ||
            platform.runtime.equals("graalvm-simulation") ||
            platform.runtime.equals("graalvm-debug")) {
            enclave.callEnclave(cmdline.getBytes());
        }
        else if (platform.runtime.equals("host")) {
            new Benchmarks(cmdline).run();
        }
        else {
            throw new NoSuchMethodException("Invalid runtime specified: " + platform.runtime);
        }
    }

    /*
     * Benchmarks
     */
    @Benchmark
    public void empty(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "empty");
    }

    @Benchmark
    public void fannkuch(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "fannkuch 10");
    }

    @Benchmark
    public void mandelbrot(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "mandelbrot 2000");
    }

    @Benchmark
    public void himeno(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "himeno S");
    }

    @Benchmark
    public void nbody(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "nbody 10000000");
    }

    @Benchmark
    public void spectral_norm(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "spectralnorm 1000");
    }

    @Benchmark
    public void binary_trees(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "binarytrees 10");
    }

    @Benchmark
    public void fasta(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "fasta 2500000");
    }

    @Benchmark
    public void pidigits(ExecutionPlatforms platform) throws NoSuchMethodException {
        runBenchmark(platform, "pidigits 1000");
    }


}

package com.r3.conclave.jvmperf;

import com.r3.conclave.common.EnclaveMode;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.EnclaveHost;

/**
 * Main class for the JVM performance tests.
 */
public class JvmPerf {


    public static void main(String[] args) throws org.openjdk.jmh.runner.RunnerException, java.io.IOException, EnclaveLoadException {

        // Check that if we are not in simulation mode then the platform actually supports hardware enclaves.
        EnclaveHost enclave = EnclaveHost.load("com.r3.conclave.avian.BenchmarkEnclave");
        if (enclave.getEnclaveMode() != EnclaveMode.SIMULATION) {
            try {
                EnclaveHost.checkPlatformSupportsEnclaves(true);
            } catch (EnclaveLoadException e) {
                throw new RuntimeException("This platform currently only supports enclaves in simulation mode.", e);
            }
        }
        enclave.close();

        // Run the benchmarks.
        org.openjdk.jmh.Main.main(args);
    }
}

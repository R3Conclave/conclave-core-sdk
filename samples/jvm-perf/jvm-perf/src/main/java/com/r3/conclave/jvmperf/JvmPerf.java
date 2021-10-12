package com.r3.conclave.jvmperf;

import com.r3.conclave.common.EnclaveMode;
import com.r3.conclave.host.PlatformSupportException;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.EnclaveHost;

/**
 * Main class for the JVM performance tests.
 */
public class JvmPerf {


    public static void main(String[] args) throws org.openjdk.jmh.runner.RunnerException, java.io.IOException, EnclaveLoadException {

        // See if we can support the hardware based tests
        try {
            EnclaveHost.enableHardwareEnclaveSupport();
        } catch (PlatformSupportException e) {
            System.out.println("This platform does not support hardware enclaves.");
            System.out.println("Please ensure you run only simulation benchmarks through the use of the 'runtime' parameter:");
            System.out.println("-p runtime=\"graalvm-simulation,host\"");
        }

        // Run the benchmarks.
        org.openjdk.jmh.Main.main(args);
    }
}

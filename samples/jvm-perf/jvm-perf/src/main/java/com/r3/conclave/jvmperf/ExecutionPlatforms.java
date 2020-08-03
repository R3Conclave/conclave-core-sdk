package com.r3.conclave.jvmperf;

import com.r3.conclave.common.OpaqueBytes;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * This class is used to describe which platform to run a benchmark on.
 */
@State(Scope.Benchmark)
public class ExecutionPlatforms {

    /**
     * The runtime to execute the benchmark on. The annotation is used to configure
     * JMH to run each benchmark on - each benchmark will be run once for each
     * runtime in the list unless overridden with the command line parameter:
     * '-p runtime=xxx'.
     */
    @Param({"avian-debug", "avian-simulation", "graalvm-debug", "graalvm-simulation", "host"})
    public String runtime;

    @Param({""})
    public String spid;

    @Param({"mock-key"})
    public String attestationKey;
}

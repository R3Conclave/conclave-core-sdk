package com.r3.conclave.threadingtest.host;

import com.r3.conclave.common.AttestationMode;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Command(name = "busy", description = "Call the BusyEnclave repeatedly from multiple threads")
public class BusyHost implements Runnable {
    @Option(names = {"-c", "--calls"}, required = true, description = "The number of times to call the enclave")
    private int calls;

    @Option(names = {"-t", "--hostThreads"}, description = "The size of the thread pool to create in the host")
    private int hostThreadPoolSize = 1;

    @Option(
            names = {"-u", "--enclaveThreads"},
            description = "The number of tasks to create each time the enclave is called " +
                    "Each task is started in a separate thread from within the enclave. " +
                    "If enclaveThreads is set to 0, a single task is started in the calling thread"
    )
    private int enclaveThreads = 1;

    @Option(names = {"-d", "--callDuration"}, description = "The duration that each task should last")
    private int callDuration = 1000;

    private EnclaveHost enclave;

    public void run() {
        startEnclave("com.r3.conclave.threadingtest.enclave.BusyEnclave");
        ExecutorService executor = Executors.newFixedThreadPool(hostThreadPoolSize);

        System.out.println("Submitting " + calls + " " + callDuration + "-millisecond busy tasks using " + hostThreadPoolSize + " threads in the host.");
        System.out.println("Each task will spawn " + enclaveThreads + " threads in the enclave");
        System.out.println();

        try {
            for (int i = 0; i < calls; i++) {
                int jobNumber = i;

                executor.submit(() -> {
                    System.out.println("Started task " + jobNumber);
                    Long duration = callEnclaveTimed(callDuration, enclaveThreads);
                    System.out.println("Completed task " + jobNumber + " in " + duration + " millis");
                });
            }
            executor.shutdown();
            executor.awaitTermination(7, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startEnclave(String enclaveClassName) {
        try {
            enclave = EnclaveHost.load(enclaveClassName);
            enclave.start(null, null, null, AttestationMode.DCAP);
        } catch (EnclaveLoadException e) {
            System.out.println("Enclave failed to start: " + e.getMessage());
        }
    }

    private Long callEnclaveTimed(int expectedDuration, int enclaveThreads) {
        Long start = System.nanoTime();
        enclave.callEnclave(ByteBuffer.allocate(8).putInt(expectedDuration).putInt(enclaveThreads).array());
        Long end = System.nanoTime();
        Long actualDurationMicros = end - start / 1000;
        return actualDurationMicros;
    }

    public static void main(String[] args) throws EnclaveLoadException {
        int exitCode = new CommandLine(new BusyHost()).execute(args);
        System.exit(exitCode);
    }
}

package com.r3.conclave.threadingtest.host;

import com.r3.conclave.common.AttestationMode;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Command(name = "fib", description = "Call the FibonacciEnclave repeatedly from multiple threads")
public class FibonacciHost implements Runnable {
    @Option(names = {"-c", "--calls"}, required = true, description = "The number of times to call the enclave")
    private int calls;

    @Option(names = {"-t", "--threads"}, description = "The size of the thread pool to create in the host")
    private int threadPoolSize = 1;

    @Option(names = "--min", required = true, description = "The minimum term to compute in the fibonacci sequence (inclusive)")
    private int minTerm;

    @Option(names = "--max", required = true, description = "The maximum term to compute in the fibonacci sequence (inclusive)")
    private int maxTerm;

    private final Random random = new Random();
    private final String outputDir = "output";
    private EnclaveHost enclave;

    public void run() {
        startEnclave("com.r3.conclave.threadingtest.enclave.FibonacciEnclave");
        String outputFileName = createOutputFile();
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        System.out.println("Calculating terms between " + minTerm + " and " + maxTerm + ", " + calls + " times, using " + threadPoolSize + " threads.");
        System.out.println();

        try (FileWriter outputFileWriter = new FileWriter(outputFileName, true)) {
            outputFileWriter.append("JOB_NUMBER,TERM,DURATION\n");

            for (int i = 0; i < calls; i++) {
                int jobNumber = i;
                int termToCompute = random.nextInt(maxTerm - minTerm + 1) + minTerm;

                executor.submit(() -> {
                    System.out.println("Started task " + jobNumber);

                    Long duration = callEnclaveTimed(termToCompute);
                    String outputRow = jobNumber + "," + termToCompute + "," + duration + "\n";
                    tryAppendingRow(outputFileWriter, outputRow);

                    System.out.println("Completed task " + jobNumber);
                });

            }
            executor.shutdown();
            executor.awaitTermination(7, TimeUnit.DAYS);
        } catch (InterruptedException | IOException e) {
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

    private String createOutputFile() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String testStart = dtf.format(LocalDateTime.now());
        String outputFileName = String.format("%s/threading_test_data_%s_%s_%s_%s_%s.csv", outputDir, calls, threadPoolSize, minTerm, maxTerm, testStart);
        System.out.println("Output file: " + outputFileName);
        final File file = new File(outputFileName);
        final File parentDir = file.getParentFile();
        if (null != file.getParentFile()) {
            parentDir.mkdirs();
        }
        return outputFileName;
    }

    private Long callEnclaveTimed(int term) {
        Long start = System.nanoTime();
        enclave.callEnclave(ByteBuffer.allocate(4).putInt(term).array());
        Long end = System.nanoTime();
        Long durationMicros = (end - start) / 1000;
        return durationMicros;
    }

    private void tryAppendingRow(FileWriter outputFileWriter, String outputRow) {
        try {
            outputFileWriter.append(outputRow);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws EnclaveLoadException {
        int exitCode = new CommandLine(new FibonacciHost()).execute(args);
        System.exit(exitCode);
    }
}

package com.r3.conclave.threadingtest.enclave;

import com.r3.conclave.enclave.Enclave;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Increments a counter in a loop to simulate real work. Multiple threads can be created inside the enclave,
 * each of which will perform this computation, and will return after the specified duration.
 *
 * @param bytes a byteArray containing two ints
 *              - the duration that each thread should last
 *              - the number of threads to create
 */
public class BusyEnclave extends Enclave {
    @Override
    protected byte[] receiveFromUntrustedHost(byte[] bytes) {
        ByteBuffer inputBuffer = ByteBuffer.wrap(bytes);
        int millis = inputBuffer.getInt();
        int enclaveThreadPoolSize = inputBuffer.getInt();

        if (enclaveThreadPoolSize == 0) {
            busyWait(millis);

        } else {
            ExecutorService executor = Executors.newFixedThreadPool(enclaveThreadPoolSize);
            for (int i = 0; i < enclaveThreadPoolSize; i++) {
                executor.execute(() -> busyWait(millis));
            }
            executor.shutdown();

            try {
                executor.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    public void busyWait(int durationMillis) {
        long counter = 1;
        System.out.println("Waiting for " + durationMillis + " millis");
        long end = System.currentTimeMillis() + durationMillis;
        while (System.currentTimeMillis() < end) {
            counter++;
        }
    }
}
package com.r3.sgx.loadtest.sampler.rng

import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.SampleResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

abstract class AbstractGrpcPoolingSampler : AbstractJavaSamplerClient() {
    companion object {
        const val ARGUMENT_ADDRESS = "Address"
        const val ARGUMENT_TIMEOUT_S = "Timeout (s)"
        val log: Logger = LoggerFactory.getLogger(AbstractGrpcPoolingSampler::class.java)
    }

    private val channelPool = LinkedBlockingQueue<ManagedChannel>()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val executor = Executors.newCachedThreadPool()

    abstract fun runTest(context: JavaSamplerContext, stub: EnclaveletHostGrpc.EnclaveletHostStub)

    override fun getDefaultParameters(): Arguments {
        val arguments = Arguments()
        arguments.addArgument(ARGUMENT_ADDRESS, "localhost:8080")
        arguments.addArgument(ARGUMENT_TIMEOUT_S, "10")
        return arguments
    }

    final override fun runTest(context: JavaSamplerContext): SampleResult {
        val channel = channelPool.poll() ?: createStub(context)
        val stub = EnclaveletHostGrpc.newStub(channel).withWaitForReady().withCompression("gzip")
        try {
            val result = SampleResult()
            result.sampleStart()
            try {
                val timeoutDuration = Duration.ofSeconds(java.lang.Long.parseLong(context.getParameter(ARGUMENT_TIMEOUT_S)))
                val future = timeout(timeoutDuration) {
                    runTest(context, stub)
                }
                if (future.get() != null) {
                    result.isSuccessful = true
                } else {
                    log.error("Sample timeout ($timeoutDuration) while running ${javaClass.simpleName}")
                }
            } catch (exception: Exception) {
                log.error("Exception while running ${javaClass.simpleName}", exception)
                result.isSuccessful = false
            }
            result.sampleEnd()
            result.latencyEnd()
            return result
        } finally {
            channelPool.add(channel)
        }
    }

    override fun teardownTest(context: JavaSamplerContext) {
        for (channel in channelPool) {
            channel.shutdownNow()
        }
        scheduledExecutor.shutdownNow()
        executor.shutdownNow()
        super.teardownTest(context)
    }

    private fun createStub(context: JavaSamplerContext): ManagedChannel {
        val address = context.getParameter(ARGUMENT_ADDRESS)
        return ManagedChannelBuilder.forTarget(address).usePlaintext().build()
    }

    private fun <A : Any> timeout(duration: Duration, block: () -> A): CompletableFuture<A?> {
        val finish = AtomicBoolean(false)
        val future = CompletableFuture<A?>()
        val timeoutClosure = {
            if (finish.compareAndSet(false, true)) {
                future.complete(null)
            }
        }
        val cancel = scheduledExecutor.schedule(timeoutClosure, duration.seconds, TimeUnit.SECONDS)
        executor.execute {
            try {
                val result = block()
                if (finish.compareAndSet(false, true)) {
                    future.complete(result)
                    cancel.cancel(true)
                }
            } catch (exception: Exception) {
                if (finish.compareAndSet(false, true)) {
                    future.completeExceptionally(exception)
                    cancel.cancel(true)
                }
            }
        }
        return future
    }
}

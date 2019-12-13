package com.r3.sgx.enclavelethost.server

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.r3.sgx.core.host.internal.NativeLoader
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.server.internal.*
import io.grpc.ServerBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.*

class EnclaveletHost(val enclavelet: EnclaveletState.Created,
                     val configuration: EnclaveletHostConfiguration): AutoCloseable, ExceptionListener {
    private val grpcService: EnclaveletHostGrpc.EnclaveletHostImplBase
    private val grpcServer: io.grpc.Server
    private val threadPool: ScheduledThreadPoolExecutor
    private var enclaveExceptions = LinkedBlockingQueue<Throwable>()

    companion object {
        val log: Logger = LoggerFactory.getLogger(EnclaveletHost::class.java)
    }

    init {
    	// Customize thread factory to assign more meaningful names
        assert(configuration.threadPoolSize > 0)
        threadPool = ScheduledThreadPoolExecutor(configuration.threadPoolSize,
                ThreadFactoryBuilder().setNameFormat("GRPC-EXECUTOR-%d").build())
        grpcService = EnclaveletRpcImpl(enclavelet, this)
        grpcServer = ServerBuilder.forPort(configuration.bindPort)
                .executor(threadPool)
                .addService(grpcService)
                .build()
        log.info("Enclavelet host initialized")
    }

    fun start(): EnclaveletHost {
        grpcServer.start()
        log.info("Enclavelet host started")
        return this
    }

    fun await() {
        val exception = enclaveExceptions.take()
        throw exception
    }

    override fun notifyException(throwable: Throwable) {
        enclaveExceptions.add(throwable)
    }

    override fun close() {
        log.info("Shutdown...")
        grpcServer.shutdownNow()
        threadPool.shutdownNow()
        log.info("Enclavelet host terminated")
    }
}
package com.r3.sgx.loadtest.sampler.rng

import io.grpc.stub.StreamObserver
import java.util.concurrent.ArrayBlockingQueue

class QueuingStreamObserver<A>(private val queue: ArrayBlockingQueue<StreamMessage<A>>) : StreamObserver<A> {
    override fun onNext(message: A) {
        queue.put(StreamMessage.Next(message))
    }

    override fun onError(throwable: Throwable) {
        queue.put(StreamMessage.Error(throwable))
    }

    override fun onCompleted() {
        queue.put(StreamMessage.Completed())
    }
}

sealed class StreamMessage<A> {
    data class Next<A>(val next: A) : StreamMessage<A>()
    data class Error<A>(val error: Throwable) : StreamMessage<A>()
    class Completed<A> : StreamMessage<A>()
}

inline fun <reified A> Any.cast(): A {
    return this as? A ?: throw IllegalArgumentException("Expected a ${A::class.java.simpleName}, got $this")
}

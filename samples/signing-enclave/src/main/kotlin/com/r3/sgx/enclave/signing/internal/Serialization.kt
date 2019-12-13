package com.r3.sgx.enclave.signing.internal

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.P2P
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.internal._inheritableContextSerializationEnv
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializerFactory


/**
 * Some boilerplate for setting up the Corda serialization machine
 */
class MyAMQPSerializationScheme :
        AbstractAMQPSerializationScheme(emptySet(), emptySet(), AccessOrderLinkedHashMap { 128 }) {

    companion object {

        fun createSerializationEnv(classLoader: ClassLoader? = null): SerializationEnvironment {
            return SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(MyAMQPSerializationScheme())
                },
                p2pContext = if (classLoader != null) AMQP_P2P_CONTEXT.withClassLoader(classLoader) else AMQP_P2P_CONTEXT
            )
        }
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return canDeserializeVersion(magic) && target == P2P
    }

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException("Client serialization not supported")
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException("Server serialization not supported")
    }
}

fun <T> SerializationEnvironment.asContextEnv(inheritable: Boolean = false, callable: (SerializationEnvironment) -> T): T {
    val property = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }
}

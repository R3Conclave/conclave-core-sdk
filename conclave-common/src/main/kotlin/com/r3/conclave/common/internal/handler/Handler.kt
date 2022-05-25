package com.r3.conclave.common.internal.handler

import java.nio.ByteBuffer

/**
 * This class abstracts the receiver end of a two-way byte channel. It is similar to a Netty handler.
 *
 * At the bottom of a handler is a [Sender], which deals with serialization and sending of raw bytes. The initialization
 * of a [Handler] involves connecting it to a low-level [Sender] that sends bytes upstream. [connect] then returns a
 * [Handler]-specific [CONNECTION] data structure that represents the connected handler. [Handler]s may use this
 * data structure for example to expose adding other downstream [Handler]s, an example being [MuxingHandler], or expose
 * top-level sending functions, an example being [SimpleProtoHandler].
 *
 * Once connected, the handler will receive bytes from upstream through the [onReceive] function.
 *
 * Users can thus build up a tree of [Handler]s that handle low-level [ByteBuffer]s. The other side of communication
 * must build up a similar tree that mirrors this side.
 *
 * The intent of this interface and implementations is to allow composition of serialization and dispatch.
 *
 * As an example we may have the following [Handler] structure:
 *
 *   Host                                    Enclave
 *
 * A--\                                          /--A'
 *    |                                          |
 * B--E---\                                 /----E'-B'
 *    |   |                                 |    |
 * C--/   F--bottom ~ ECALL/OCALL ~ bottom--F'   \--C'
 *        |                                 |
 * D------/                                 \-------D'
 *
 * Here we have handlers A,B,C,D,E and F on the host side, and corresponding ones on the other. When say A wants to send
 * something to the enclave it will call through the [Handler]/[Sender] tree where each component will serialize its
 * part of the message. For example we may end up with a message like this:
 *
 * +----------+
 * | F header |
 * +----------+
 * | E header |
 * +----------+
 * |  A body  |
 * |   ...    |
 * +----------+
 *
 * When the message is sent it will first be handled by F' which will deserialize its part and forward it to its
 * downstream E', then A', etc.
 *
 * @param CONNECTION The type representing the connected state of the [Handler], once it's been wired up.
 */
interface Handler<CONNECTION> {
    /**
     * Connect to [upstream], returning the connection data structure.
     */
    fun connect(upstream: Sender): CONNECTION

    /**
     * Receive bytes from upstream.
     * @param input The byte buffer containing the bytes. The buffer may become invalid and cannot be used after this
     * method returns. If bytes need to be used after this method they must be copied from the buffer.
     */
    fun onReceive(connection: CONNECTION, input: ByteBuffer)
}

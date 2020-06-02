package com.r3.conclave.common.internal.handler

/**
 * The discriminator byte used by the [ChannelHandlingHandler]/[ChannelHandlingHandler] pair.
 */
enum class ChannelDiscriminator(val value: Byte) {
    OPEN(0),
    CLOSE(1),
    PAYLOAD(2)
}
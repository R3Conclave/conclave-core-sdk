package com.r3.sgx.core.common

/**
 * The discriminator byte used by the [ChannelHandlingHandler]/[ChannelHandlingHandler] pair.
 */
enum class ChannelDiscriminator(val value: Byte) {
    OPEN(0),
    CLOSE(1),
    PAYLOAD(2)
}
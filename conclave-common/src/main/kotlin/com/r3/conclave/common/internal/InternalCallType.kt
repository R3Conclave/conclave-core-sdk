package com.r3.conclave.common.internal

/**
 * Byte used to disambiguate different kinds of messages on the enclave message handler.
 */
enum class InternalCallType {
    /** On the inbound path represents `EnclaveHost.callEnclave` and on the outbound `Enclave.callUntrustedHost`. */
    UNTRUSTED_HOST,

    /** Bytes returned from [java.util.function.Function.apply] callback. */
    CALL_RETURN,

    /**
     * On the inbound-to-enclave path, contains a mail to be decrypted. On the outbound path, contains a mail command.
     */
    MAIL
}

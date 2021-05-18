package com.r3.conclave.common.internal

/**
 * Byte used to disambiguate different kinds of messages on the enclave message handler.
 */
enum class InternalCallType {
    /** Top level call */
    CALL,

    /** Bytes returned from [java.util.function.Function.apply] callback. */
    CALL_RETURN,

    /**
     * On the inbound-to-enclave path, contains a mail to be decrypted. On the outbound path, contains a mail command.
     */
    MAIL_DELIVERY,

    /**
     * Delivered by the enclave for the host to store, containing sealed data of mail the enclave has acknowledged.
     * This is to ensure mail redelivery still works in light of seq number checks.
     */
    ACKNOWLEDGEMENT_RECEIPT
}

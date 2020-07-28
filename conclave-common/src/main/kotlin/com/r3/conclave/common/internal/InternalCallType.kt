package com.r3.conclave.common.internal

import com.r3.conclave.common.EnclaveCall

/**
 * Byte used to disambiguate different kinds of messages on the enclave message handler.
 */
enum class InternalCallType {
    /** Top level call */
    CALL,
    /** Bytes returned from [EnclaveCall.invoke]. */
    CALL_RETURN,
    /**
     * On the inbound-to-enclave path, contains a mail to be decrypted. On the outbound path, contains a serialised
     * [MailCommand].
     */
    MAIL_DELIVERY
}
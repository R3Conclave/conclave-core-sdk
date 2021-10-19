package com.r3.conclave.mail

import java.security.GeneralSecurityException

/**
 * Exception if mail cannot be decrypted either due to key mismatch or corrupted mail bytes.
 */
class MailDecryptionException(message: String?, cause: Throwable?) : GeneralSecurityException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}

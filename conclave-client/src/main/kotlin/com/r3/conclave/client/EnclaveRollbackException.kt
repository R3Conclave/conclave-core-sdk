package com.r3.conclave.client

import com.r3.conclave.mail.EnclaveMail
import java.lang.RuntimeException

/**
 * Exception that's thrown by [EnclaveClient] if it detects the enclave's state has been rolled back.
 *
 * @property mail If there was a mail from the enclave as part of the rollback detection. Note this response may well be
 * insecure as the enclave will have been working off an an out of date state. This will be null if there was no mail.
 *
 * @see EnclaveClient.ignoreEnclaveRollback
 */
class EnclaveRollbackException(message: String, val mail: EnclaveMail?) : RuntimeException(message)

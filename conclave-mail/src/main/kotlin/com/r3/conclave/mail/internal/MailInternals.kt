package com.r3.conclave.mail.internal

import com.r3.conclave.mail.MutableMail

// Access for conclave-common and conclave-enclave
fun MutableMail.setKeyDerivation(keyDerivation: ByteArray) {
    this.`internal keyDerivation` = keyDerivation
}

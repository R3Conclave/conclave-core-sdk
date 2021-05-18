package com.r3.conclave.common.internal

enum class MailCommandType {
    POST,
    ACKNOWLEDGE,
    RECEIPT
}

class AcknowledgementReceiptVersion {
    companion object {
        // reserved for future use
        // current value correspond to Conclave release version - 1.1
        const val value: Int = 0x01010000
    }
}


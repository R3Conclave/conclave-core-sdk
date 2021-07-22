package com.r3.conclave.integrationtests.general.enclave

open class SealUnsealEnclave2 : SealUnsealEnclave() {

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        return runSealUnsealFromBytes(bytes)
    }
}


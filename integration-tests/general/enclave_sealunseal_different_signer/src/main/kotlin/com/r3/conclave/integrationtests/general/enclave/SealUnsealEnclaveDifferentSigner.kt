package com.r3.conclave.integrationtests.general.enclave

class SealUnsealEnclaveDifferentSigner : SealUnsealEnclave() {

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        return runSealUnsealFromBytes(bytes)
    }
}

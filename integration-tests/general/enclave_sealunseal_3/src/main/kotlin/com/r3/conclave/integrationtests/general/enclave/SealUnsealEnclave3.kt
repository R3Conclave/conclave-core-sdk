package com.r3.conclave.integrationtests.general.enclave

class SealUnsealEnclave3 : SealUnsealEnclave() {

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        return runSealUnsealFromBytes(bytes)
    }
}

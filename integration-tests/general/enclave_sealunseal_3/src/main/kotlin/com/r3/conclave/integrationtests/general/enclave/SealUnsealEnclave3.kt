package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.integrationtests.general.common.SealUnsealEnclave

class SealUnsealEnclave3 : SealUnsealEnclave() {

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        return runSealUnsealFromBytes(bytes)
    }
}

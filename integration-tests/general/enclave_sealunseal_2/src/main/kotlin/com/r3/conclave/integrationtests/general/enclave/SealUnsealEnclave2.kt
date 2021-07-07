package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.integrationtests.general.common.SealUnsealEnclave
import com.r3.conclave.integrationtests.general.common.TestHelper

class SealUnsealEnclave2 : SealUnsealEnclave() {

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        return runSealUnsealFromBytes(bytes)
    }
}

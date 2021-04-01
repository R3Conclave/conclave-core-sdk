package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.PostOffice

class NonThreadSafeEnclaveWithPostOffice : Enclave() {

    override fun getThreadSafe(): Boolean {
        return false
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        when (String(bytes)) {
            "PostOffice.create()" -> PostOffice.create(enclaveInstanceInfo.encryptionKey)
            "EnclaveInstanceInfo.createPostOffice()" -> enclaveInstanceInfo.createPostOffice()
            "EnclaveInstanceInfo.serialize()" -> return enclaveInstanceInfo.serialize()
        }
        return null
    }
}


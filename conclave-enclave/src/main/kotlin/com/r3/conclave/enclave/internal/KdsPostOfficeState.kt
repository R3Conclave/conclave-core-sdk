package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType

class KdsPostOfficeState(val generatedKdsPostOfficePolicyConstraint: EnclaveConstraint, val masterKeyType: MasterKeyType) {
    var postOfficeKdsPrivateKey: ByteArray? = null
}

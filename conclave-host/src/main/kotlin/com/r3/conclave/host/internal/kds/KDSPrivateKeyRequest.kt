package com.r3.conclave.host.internal.kds

import com.r3.conclave.common.kds.MasterKeyType

class KDSPrivateKeyRequest constructor(
    val appAttestationReport: ByteArray,

    val name: String,

    val masterKeyType: MasterKeyType,

    val policyConstraint: String
)

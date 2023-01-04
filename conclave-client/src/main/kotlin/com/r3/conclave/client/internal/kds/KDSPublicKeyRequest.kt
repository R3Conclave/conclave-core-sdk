package com.r3.conclave.client.internal.kds

import com.r3.conclave.common.kds.MasterKeyType

class KDSPublicKeyRequest constructor(
    val name: String,
    val masterKeyType: MasterKeyType,
    val policyConstraint: String
)

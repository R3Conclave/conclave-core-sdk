package com.r3.conclave.internaltesting.kds.api.request

import com.r3.conclave.common.kds.MasterKeyType
import kotlinx.serialization.Serializable


@Serializable
data class PublicKeyRequest(val name: String, val masterKeyType: MasterKeyType, val policyConstraint: String)

@Serializable
data class PrivateKeyRequest(val appAttestationReport: String, val name: String, val masterKeyType: MasterKeyType, val policyConstraint: String)

package com.r3.conclave.internaltesting.kds

import com.r3.conclave.common.kds.MasterKeyType
import kotlinx.serialization.Serializable

@Serializable
data class PublicKeyRequest(
    override val name: String,
    override val masterKeyType: MasterKeyType,
    override val policyConstraint: String
) : KdsKeySpec

@Serializable
data class PrivateKeyRequest(
    val appAttestationReport: String,
    override val name: String,
    override val masterKeyType: MasterKeyType,
    override val policyConstraint: String
) : KdsKeySpec

interface KdsKeySpec {
    val name: String
    val masterKeyType: MasterKeyType
    val policyConstraint: String
}

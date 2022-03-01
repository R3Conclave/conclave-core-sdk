package com.r3.conclave.common.internal.kds

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.r3.conclave.common.internal.SgxCpuSvn
import com.r3.conclave.common.internal.SgxIsvSvn
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.readIntLengthPrefixString
import com.r3.conclave.utilities.internal.writeIntLengthPrefixString
import java.io.DataInputStream
import java.io.DataOutputStream

object KDSUtils {
    private const val PADDING_CHAR = 0
    val ABANDONED_HEADER_SIZE = SgxCpuSvn.size + SgxIsvSvn.size
    private val KDS_JSON_MAPPER: JsonMapper =
        JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build()
    private val masterKeyValues = MasterKeyType.values()

    fun serializeKeySpec(keyDerivationStream: DataOutputStream, kdsSpec: KDSKeySpec) {
        keyDerivationStream.writeIntLengthPrefixString(kdsSpec.name)
        keyDerivationStream.writeByte(kdsSpec.masterKeyType.ordinal)
        keyDerivationStream.writeIntLengthPrefixString(kdsSpec.policyConstraint)

        if (keyDerivationStream.size() == ABANDONED_HEADER_SIZE) {
            keyDerivationStream.writeByte(PADDING_CHAR)
        }
    }

    fun deserializeKeySpec(keyDerivationStream: DataInputStream): KDSKeySpec {
        try {
            val name = keyDerivationStream.readIntLengthPrefixString()
            val masterKeyType = masterKeyValues[keyDerivationStream.readByte().toInt()]
            val policyConstraint = keyDerivationStream.readIntLengthPrefixString()
            return KDSKeySpec(name, masterKeyType, policyConstraint)
        } catch (e: Exception) {
            throw MailDecryptionException(
                "Cannot deserialize the key derivation header, the mail has been tampered with!",
                e
            )
        }
    }

    @JvmStatic
    fun getJsonMapper(): JsonMapper {
        return KDS_JSON_MAPPER;
    }
}

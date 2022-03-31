package com.r3.conclave.common.internal

import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.internal.MailDecryptingStream
import com.r3.conclave.utilities.internal.dataStream
import com.r3.conclave.utilities.internal.readIntLengthPrefixString
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixString
import java.io.DataInputStream
import java.io.DataOutputStream

sealed class MailKeyDerivation {
    companion object {
        const val RANDOM_SESSION_TYPE = 0
        const val KDS_KEY_SPEC_TYPE = 1
        /**
         * The fixed header size of the abandoned MRSIGNER key derivation header. It is comprised of the CPUSVN and
         * ISVSVN (i.e. revocation level) values used in the key derivation.
         *
         * Since CPUSVN is essentially random and the ISVSVN is typically a common integer value, there's no way to
         * determine by looking at the bytes that it's a MRSIGNER key derivation header. The only information we have to
         * go on is the size, which is fixed. The enclave thus uses this fixed size to determine if the key derivation
         * is MRSIGNER or not. This thus means any other type of derivation header must not equal this size.
         */
        val ABANDONED_MRSIGNER_SIZE = SgxCpuSvn.size + SgxIsvSvn.size

        fun deserialiseFromMailBytes(mailBytes: ByteArray): MailKeyDerivation {
            return deserialiseFromMailStream(MailDecryptingStream(mailBytes))
        }

        fun deserialiseFromMailStream(mailStream: MailDecryptingStream): MailKeyDerivation {
            val keyDerivation = mailStream.header.keyDerivation
            if (keyDerivation == null || keyDerivation.size == ABANDONED_MRSIGNER_SIZE) {
                // Though MRSIGNER-derived encryption keys for Mail was dropped in 1.2, the client code wasn't
                // updated to not populate the key derivation header and so any clients using 1.2 will still be
                // sending in Mail with these abandoned key derivation values.
                return RandomSessionKeyDerivation
            }
            val keyDerivationStream = keyDerivation.dataStream()
            val keyDerivationType: Int
            try {
                keyDerivationType = keyDerivationStream.readByte().toInt()
                if (keyDerivationType == RANDOM_SESSION_TYPE) {
                    return RandomSessionKeyDerivation
                } else if (keyDerivationType == KDS_KEY_SPEC_TYPE) {
                    return KdsKeySpecKeyDerivation.deserialise(keyDerivationStream)
                }
            } catch (e: Exception) {
                throw MailDecryptionException("Cannot deserialize the key derivation header", e)
            }
            throw MailDecryptionException("Unknown key derivation type $keyDerivationType")
        }
    }

    abstract val type: Int

    fun serialise(): ByteArray {
        return writeData {
            writeByte(type)
            serialise(this)
        }
    }

    protected abstract fun serialise(dos: DataOutputStream)
}

object RandomSessionKeyDerivation : MailKeyDerivation() {
    override val type: Int get() = RANDOM_SESSION_TYPE
    override fun serialise(dos: DataOutputStream) {
        // There's nothing more to tell the enclave if the random session key is being used.
    }
}

class KdsKeySpecKeyDerivation(val keySpec: KDSKeySpec) : MailKeyDerivation() {
    companion object {
        private val masterKeyTypeValues = MasterKeyType.values()

        fun deserialise(dis: DataInputStream): KdsKeySpecKeyDerivation {
            val name = dis.readIntLengthPrefixString()
            val masterKeyType = masterKeyTypeValues[dis.readByte().toInt()]
            val policyConstraint = dis.readIntLengthPrefixString()
            return KdsKeySpecKeyDerivation(KDSKeySpec(name, masterKeyType, policyConstraint))
        }
    }

    override val type: Int get() = KDS_KEY_SPEC_TYPE

    override fun serialise(dos: DataOutputStream) {
        dos.writeIntLengthPrefixString(keySpec.name)
        dos.writeByte(keySpec.masterKeyType.ordinal)
        dos.writeIntLengthPrefixString(keySpec.policyConstraint)
        // Make sure the serialised size isn't the same as the old MRSIGNER header, or otherwise the enclave will
        // treat it as one.
        if (dos.size() == ABANDONED_MRSIGNER_SIZE) {
            dos.writeByte(0)
        }
    }
}

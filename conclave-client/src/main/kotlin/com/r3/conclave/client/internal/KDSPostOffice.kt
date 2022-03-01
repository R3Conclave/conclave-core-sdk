package com.r3.conclave.client.internal

import com.r3.conclave.common.internal.kds.KDSUtils
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.mail.internal.MailKeyDerivationType
import com.r3.conclave.utilities.internal.writeData
import java.security.PrivateKey
import java.security.PublicKey

class KDSPostOffice(
    override val destinationPublicKey: PublicKey,
    senderPrivateKey: PrivateKey,
    topic: String,
    private val keySpec: KDSKeySpec
) : PostOffice(senderPrivateKey, topic) {

    init {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(destinationPublicKey is Curve25519PublicKey) {
            "At this time only Conclave originated Curve25519 public keys may be used."
        }
    }

    override val keyDerivation: ByteArray = serializeKeyDerivation()

    private fun serializeKeyDerivation(): ByteArray {

        return writeData {
            writeByte(MailKeyDerivationType.KDS_KEY_SPEC.ordinal)
            KDSUtils.serializeKeySpec(this, keySpec)
        }
    }
}

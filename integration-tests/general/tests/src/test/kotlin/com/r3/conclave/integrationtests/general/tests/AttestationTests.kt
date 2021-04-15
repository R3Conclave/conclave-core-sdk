package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxEnclaveMetadata.enclaveCss
import com.r3.conclave.common.internal.SgxMetadataCssBody.enclaveHash
import com.r3.conclave.common.internal.SgxMetadataCssKey.modulus
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.body
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.key
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.Native
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class AttestationTests : JvmTest("com.r3.conclave.integrationtests.general.enclave.NonThreadSafeEnclaveWithPostOffice") {

    @Test
    fun `enclave info`() {
        // TODO consider using Java/Kotlin code to read ELF file - https://github.com/fornwall/jelf/
        val metadataCursor = Cursor.allocate(SgxEnclaveMetadata.INSTANCE)
        Native.INSTANCE.getMetadata(getEnclaveFilename(), metadataCursor.buffer.array())

        val metadata = Cursor.wrap(SgxEnclaveMetadata.INSTANCE, metadataCursor.buffer.array(), 0, metadataCursor.size)
        val metaCodeHash = SHA256Hash.get(metadata[enclaveCss][body][enclaveHash].read())
        val metaCodeSigningKeyHash = SHA256Hash.hash(metadata[enclaveCss][key][modulus].bytes)

        enclaveHost.enclaveInstanceInfo.enclaveInfo.apply {
            assertThat(codeHash).isEqualTo(metaCodeHash)
            assertThat(codeSigningKeyHash).isEqualTo(metaCodeSigningKeyHash)
        }
    }

    private fun getEnclaveFilename(): String {
        val enclaveHandleField = EnclaveHost::class.java.getDeclaredField("enclaveHandle").apply { isAccessible = true }
        val enclaveHandle = enclaveHandleField.get(enclaveHost)

        val enclaveFileField = enclaveHandle.javaClass.getDeclaredField("enclaveFile").apply { isAccessible = true }
        return enclaveFileField.get(enclaveHandle).toString()
    }

    @Test
    fun `EnclaveInstanceInfo serialisation round-trip`() {
        val roundTrip = EnclaveInstanceInfo.deserialize(enclaveHost.enclaveInstanceInfo.serialize())
        assertThat(roundTrip).isEqualTo(enclaveHost.enclaveInstanceInfo)
    }

    @Test
    fun `EnclaveInstanceInfo deserialize throws IllegalArgumentException on truncated bytes`() {
        val serialised = enclaveHost.enclaveInstanceInfo.serialize()
        for (truncatedSize in serialised.indices) {
            val truncated = serialised.copyOf(truncatedSize)
            val thrownBy = assertThatIllegalArgumentException().describedAs("Truncated size $truncatedSize")
                                .isThrownBy { EnclaveInstanceInfo.deserialize(truncated) }

            if (truncatedSize > 3) {
                    thrownBy.withMessage("Truncated EnclaveInstanceInfo bytes")
            } else {
                    thrownBy.withMessage("Not EnclaveInstanceInfo bytes")
            }
        }
    }

    @Test
    fun `EnclaveInstanceInfo matches across host and enclave`() {
        val eiiFromHost = enclaveHost.enclaveInstanceInfo
        val eiiFromEnclave = EnclaveInstanceInfo.deserialize(enclaveHost.callEnclave("EnclaveInstanceInfo.serialize()".toByteArray())!!)
        assertThat(eiiFromEnclave).isEqualTo(eiiFromHost)
    }
}
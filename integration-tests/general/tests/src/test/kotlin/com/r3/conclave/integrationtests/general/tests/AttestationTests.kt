package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxCssBody.*
import com.r3.conclave.common.internal.SgxEnclaveCss.body
import com.r3.conclave.common.internal.SgxEnclaveCss.key
import com.r3.conclave.common.internal.SgxQuote.reportBody
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.tasks.CreateAttestationQuoteAction
import com.r3.conclave.integrationtests.general.common.tasks.GetEnclaveInstanceInfo
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAALVM
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAMINE
import com.r3.conclave.integrationtests.general.commontest.TestUtils.debugOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.simulationOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.tomlj.Toml
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.readBytes

class AttestationTests : AbstractEnclaveActionTest() {
    @Test
    fun `debug mode uses DCAP`() {
        debugOnlyTest()
        val enclaveInstanceInfo = enclaveHost().enclaveInstanceInfo as EnclaveInstanceInfoImpl
        assertThat(enclaveInstanceInfo.attestation).isInstanceOf(DcapAttestation::class.java)
    }

    @Test
    fun `simulation mode uses mock`() {
        simulationOnlyTest()
        val enclaveInstanceInfo = enclaveHost().enclaveInstanceInfo as EnclaveInstanceInfoImpl
        assertThat(enclaveInstanceInfo.attestation).isInstanceOf(MockAttestation::class.java)
    }

    @Test
    fun `EnclaveInfo matches SIGSTRUCT`() {
        val sigstruct = when (TestUtils.runtimeType) {
            GRAALVM -> TestUtils.getEnclaveSigstruct(enclaveHost().getEnclaveBundlePath("enclaveFile"))
            GRAMINE -> {
                debugOnlyTest()  // Gramine doesn't simulate the SIGSTRUCT, unlike the Intel SDK
                val gramineWorkingDir = enclaveHost().getEnclaveBundlePath("workingDirectory")
                Cursor.wrap(SgxEnclaveCss.INSTANCE, (gramineWorkingDir / "java.sig").readBytes())
            }
        }

        with(enclaveHost().enclaveInstanceInfo.enclaveInfo) {
            assertThat(codeHash).isEqualTo(SHA256Hash.get(sigstruct[body][enclaveHash].read()))
            assertThat(codeSigningKeyHash).isEqualTo(SgxTypesKt.getMrsigner(sigstruct[key]))
            assertThat(productID).isEqualTo(sigstruct[body][IsvProdId].read())
            assertThat(revocationLevel).isEqualTo(sigstruct[body][IsvSvn].read() - 1)
            assertThat(enclaveMode).isEqualTo(TestUtils.enclaveMode.toEnclaveMode())
        }
    }

    @Test
    fun `EnclaveInfo matches in simulation gramine`() {
        gramineOnlyTest()
        simulationOnlyTest()

        val gramineWorkingDir = enclaveHost().getEnclaveBundlePath("workingDirectory")
        val manifest = Toml.parse(gramineWorkingDir / "java.manifest")

        with(enclaveHost().enclaveInstanceInfo.enclaveInfo) {
            assertThat(codeHash).isEqualTo(SHA256Hash.hash((gramineWorkingDir / "enclave.jar").readBytes()))
            assertThat(codeSigningKeyHash).isEqualTo(SHA256Hash.parse(manifest.getString("loader.env.CONCLAVE_SIMULATION_MRSIGNER")!!))
            assertThat(productID).isEqualTo(manifest.getLong("sgx.isvprodid")!!.toInt())
            assertThat(revocationLevel).isEqualTo(manifest.getLong("sgx.isvsvn")!!.toInt() - 1)
            assertThat(enclaveMode).isEqualTo(EnclaveMode.SIMULATION)
        }
    }

    @Test
    fun `EnclaveInstanceInfo serialisation round-trip`() {
        val roundTrip = EnclaveInstanceInfo.deserialize(enclaveHost().enclaveInstanceInfo.serialize())
        assertThat(roundTrip).isEqualTo(enclaveHost().enclaveInstanceInfo)
    }

    @Test
    fun `EnclaveInstanceInfo deserialize throws IllegalArgumentException on truncated bytes`() {
        val serialised = enclaveHost().enclaveInstanceInfo.serialize()
        for (truncatedSize in serialised.indices) {
            val truncated = serialised.copyOf(truncatedSize)
            val thrownBy = assertThatIllegalArgumentException()
                .describedAs("Truncated size $truncatedSize")
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
        val eiiFromHost = enclaveHost().enclaveInstanceInfo
        val eiiFromEnclave = callEnclave(GetEnclaveInstanceInfo())
        assertThat(eiiFromEnclave).isEqualTo(eiiFromHost)
    }

    @Test
    fun `ensure the user data is contained inside the signed quote that was generated by createAttestationQuote`() {
        val reportDataOriginal = "Test".padEnd(64, ' ')
        val signedQuote = callEnclave(enclaveHost(), CreateAttestationQuoteAction(reportDataOriginal.toByteArray())) { it }
        val signedQuoteByteCursor = Cursor.wrap(SgxSignedQuote.INSTANCE, signedQuote)

        val reportDataFromSignedQuote = signedQuoteByteCursor[quote][reportBody][reportData].bytes
        assertThat(String(reportDataFromSignedQuote)).isEqualTo(reportDataOriginal)
    }

    private fun EnclaveHost.getEnclaveBundlePath(pathField: String): Path {
        val enclaveHandle = EnclaveHost::class.java.getDeclaredField("enclaveHandle")
            .apply { isAccessible = true }
            .get(this)
        return enclaveHandle.javaClass.getDeclaredField(pathField)
            .apply { isAccessible = true }
            .get(enclaveHandle) as Path
    }
}

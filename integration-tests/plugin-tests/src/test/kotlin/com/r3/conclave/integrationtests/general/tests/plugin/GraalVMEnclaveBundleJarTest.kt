package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvProdId
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvSvn
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.body
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.key
import com.r3.conclave.common.internal.SgxTypesKt
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAALVM
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.calculateMrsigner
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.execCommand
import com.r3.conclave.integrationtests.general.commontest.TestUtils.getEnclaveSigstruct
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readSigningKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.jar.JarFile
import kotlin.io.path.*

/**
 * This test is the GraalVM version of [GramineEnclaveBundleJarTest] however it's must more extensive. The reason for
 * this, and why this is the only GraalVM specific test class, is because of the lengthy build time of native image
 * when creating the unsigned enclave .so file. To avoid having a long test build time, this class only has one big
 * test which runs through various scenarios, all the while trying to keep the number of times the enclave is rebuilt
 * down to a minimum.
 */
class GraalVMEnclaveBundleJarTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "enclaveBundle${capitalisedEnclaveMode()}Jar"
    override val output: Path get() {
        return buildDir / "libs" / "$projectName-bundle-${enclaveMode.name.lowercase()}.jar"
    }
    override val taskIsSpecificToRuntime get() = GRAALVM
    override val runDeletionAndReproducibilityTest: Boolean get() = false

    private lateinit var originalUnsignedEnclaveModifiedTime: FileTime
    private lateinit var expectedMrsigner: SHA256Hash
    private var expectedProductID: Int? = null
    private var expectedRevocationLevel: Int? = null

    @Test
    fun `single test run`() {
        // Perform the first run which will build the unsigned enclave .so and then sign it with the random dummy key
        runTaskAndAssertItsIncremental()
        expectedMrsigner = calculateMrsigner(dummyKey())
        expectedProductID = 11
        expectedRevocationLevel = 12

        assertEnclave(unsignedEnclaveUnchanged = false)  // This is the first time the unsigned enclave has been built.

        println("Changing the productID and revocationLevel ...")
        testChangingEnclaveConfig()

        // Capture the modified time of the unsigned enclave file and make sure from now on it doesn't get rebuilt.
        originalUnsignedEnclaveModifiedTime = unsignedEnclave.getLastModifiedTime()

        println("Deleting the output signed enclave file ...")
        val bundleJarContentsUsingDummySigner = testDeletingSignedEnclave()

        println("Using 'privateKey' signing ...")
        testPrivateKeySigning()

        println("Using 'dummyKey' signing ...")
        testDummyKeySigning(bundleJarContentsUsingDummySigner)

        println("Using 'externalKey' signing, first with default signing material output location ...")
        testExternalSigning()
    }

    private fun testDummyKeySigning(bundleJarContentsUsingDummySigner: ByteArray) {
        // Switch back to using the dummy key but this time make it explicit in the config.
        modifyGradleBuildFile("signingType = privateKey", "signingType = dummyKey")
        expectedMrsigner = calculateMrsigner(dummyKey())
        runTaskAndAssertItsIncremental()
        assertThat(output).hasBinaryContent(bundleJarContentsUsingDummySigner)
        assertEnclave()
    }

    private fun testPrivateKeySigning() {
        val userSigningKey = Files.createTempFile(buildDir, "user_signing_key", ".pem").also {
            TestUtils.generateSigningKey(it)
        }
        expectedMrsigner = calculateMrsigner(readSigningKey(userSigningKey))

        // Insert the config block for the current enclave mode and switch to the 'privateKey' signing type.
        addEnclaveModeConfig("""
            signingType = privateKey
            signingKey = file('$userSigningKey')
        """.trimIndent())
        runTaskAndAssertItsIncremental()
        assertEnclave()
    }

    // Delete the signed enclave file and make sure it's reproducible
    private fun testDeletingSignedEnclave(): ByteArray {
        val bundleJarContentsUsingDummySigner = output.readBytes()
        output.deleteExisting()
        runTaskAndAssertItsIncremental()
        assertThat(output).hasBinaryContent(bundleJarContentsUsingDummySigner)
        assertEnclave()
        return bundleJarContentsUsingDummySigner
    }

    // Modify the productID, which will invoke native-image for the second and final time for this test
    private fun testChangingEnclaveConfig() {
        modifyProductIdConfig(12)
        modifyRevocationLevelConfig(20)
        expectedProductID = 12
        expectedRevocationLevel = 20
        runTaskAndAssertItsIncremental()
        assertEnclave(unsignedEnclaveUnchanged = false)  // The product ID and revocation level have changed
    }

    private fun testExternalSigning() {
        val userSigningKey = Files.createTempFile(buildDir, "external_signing_key", ".pem").also {
            TestUtils.generateSigningKey(it)
        }
        expectedMrsigner = calculateMrsigner(readSigningKey(userSigningKey))

        val signatureFile = Files.createTempFile(buildDir, "signature", ".bin")
        val publicKeyFile = Files.createTempFile(buildDir, "signing_public_key", ".pem")

        execCommand(
            "openssl", "rsa",
            "-in", userSigningKey.absolutePathString(),
            "-pubout", "-out", publicKeyFile.absolutePathString()
        )

        // Now use 'externalKey' signing and sign the enclave manually
        modifyGradleBuildFile(
            "signingType = dummyKey",
            """signingType = externalKey
               signatureDate = new Date(1970, 0, 1)
               mrsignerSignature = file("$signatureFile")
               mrsignerPublicKey = file("$publicKeyFile")
            """.trimIndent()
        )

        // First generate the signing material and expect the default output location to be used
        val defaultSigningMaterialFile = buildDir / "enclave" / enclaveMode.name.lowercase() / "signing_material.bin"
        assertThat(defaultSigningMaterialFile).doesNotExist()
        runTaskAndAssertItsIncremental("generateEnclaveSigningMaterial${capitalisedEnclaveMode()}")
        assertThat(defaultSigningMaterialFile).exists()
        assertUnsignedEnclaveHasntChanged()
        val signingMaterial = defaultSigningMaterialFile.readBytes()

        println("Using user provided location for signing material output...")
        val signingMaterialFile = Files.createTempFile(buildDir, "signing_material", ".bin")
        // Insert signingMaterial config
        modifyGradleBuildFile(
            "signingType = externalKey",
            "signingType = externalKey\nsigningMaterial = file('$signingMaterialFile')\n"
        )
        runTaskAndAssertItsIncremental("generateEnclaveSigningMaterial${capitalisedEnclaveMode()}")
        // The signing material should be the same
        assertThat(signingMaterialFile).hasBinaryContent(signingMaterial)

        println("Continue build process with external signed material...")
        // Sign the signing material
        execCommand(
            "openssl", "dgst", "-sha256",
            "-out", signatureFile.absolutePathString(),
            "-sign", userSigningKey.absolutePathString(),
            "-keyform", "PEM",
            signingMaterialFile.absolutePathString()
        )
        // Run the main task again and have it sign the enclave using the external signing material
        runTaskAndAssertItsIncremental()
        assertEnclave()
    }

    /**
     * @param unsignedEnclaveUnchanged By default the unsigned enclave is not expected to have changed, to keep the
     * number of times its rebuilt down to a minimum since it's expensive to build. If this is not the case when
     * asserting, then explain why with a comment.
     */
    private fun assertEnclave(unsignedEnclaveUnchanged: Boolean = true) {
        if (unsignedEnclaveUnchanged) {
            assertUnsignedEnclaveHasntChanged()
        }
        checkBundleJarContents()
        val sigstruct = getEnclaveSigstruct(signedEnclave)
        assertThat(sigstruct[body][IsvProdId].read()).isEqualTo(expectedProductID)
        assertThat(sigstruct[body][IsvSvn].read()).isEqualTo(expectedRevocationLevel!! + 1)
        assertThat(SgxTypesKt.getMrsigner(sigstruct[key])).isEqualTo(expectedMrsigner)
    }

    private fun assertUnsignedEnclaveHasntChanged() {
        assertThat(unsignedEnclave.getLastModifiedTime()).isEqualTo(originalUnsignedEnclaveModifiedTime)
    }

    private fun checkBundleJarContents() {
        val signedEnclaveBytes = JarFile(output.toFile()).use { jar ->
            val bundlePath = "com/r3/conclave/enclave/user-bundles/com.test.enclave.TestEnclave/" +
                    "${enclaveMode.name.lowercase()}-graalvm.so"
            val entry = jar.assertEntryExists(bundlePath)
            jar.getInputStream(entry).use { it.readBytes() }
        }
        assertThat(signedEnclave).hasBinaryContent(signedEnclaveBytes)
    }

    private val unsignedEnclave: Path get() = enclaveModeBuildDir / "enclave.so"

    private val signedEnclave: Path get() = enclaveModeBuildDir / "enclave.signed.so"
}

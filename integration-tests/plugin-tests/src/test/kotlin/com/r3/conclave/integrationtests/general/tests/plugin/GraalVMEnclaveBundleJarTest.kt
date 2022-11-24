package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvProdId
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvSvn
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.body
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.key
import com.r3.conclave.common.internal.SgxTypesKt
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.calculateMrsigner
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.execCommand
import com.r3.conclave.integrationtests.general.commontest.TestUtils.getEnclaveMetadata
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readSigningKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.jar.JarFile
import kotlin.io.path.*

/**
 * This test is similar to [GramineEnclaveBundleJarTest] in that it tests the enclaveBundleJar task but when the
 * runtime type is Graal VM. It also has a single big test, which tests several scenerios in succession, to reduce
 * the number of times the unsigned enclave file is built, which takes a long time and would make this test class
 * unsuitable as an integration test.
 */
class GraalVMEnclaveBundleJarTest : AbstractTaskTest(), TestWithSigning {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() = "enclaveBundle${capitalisedMode}Jar"

    override val output: Path get() {
        return buildDir / "libs" / "$projectName-bundle-${enclaveMode.name.lowercase()}.jar"
    }

    override val runDeletionAndReproducibilityTest: Boolean get() = false

    private val capitalisedMode: String get() = enclaveMode.name.lowercase().replaceFirstChar(Char::titlecase)

    private lateinit var originalUnsignedEnclaveModifiedTime: FileTime

    @Test
    fun `single test run`() {
        // Perform the first run which will build the unsigned enclave so and then sign it with the random dummy key
        runTaskAndAssertItsIncremental()
        val dummyMrsigner = calculateMrsigner(readSigningKey(dummyKeyFile))

        assertSignedEnclave(expectedMrsigner = dummyMrsigner, expectedProductID = 11, expectedRevocationLevel = 12)

        println("Changing the productID and revocationLevel ...")
        testChangingEnclaveConfig(expectedMrsigner = dummyMrsigner)

        // Capture the modified time of the unsigned enclave file and make sure from now on it doesn't get rebuilt.
        originalUnsignedEnclaveModifiedTime = unsignedEnclave.getLastModifiedTime()

        println("Deleting the output signed enclave file ...")
        val bundleJarContentsUsingDummySigner = testDeletingSignedEnclave(expectedMrsigner = dummyMrsigner)

        println("Using 'privateKey' signing ...")
        testPrivateKeySigning()

        println("Using 'dummyKey' signing ...")
        testDummyKeySigning(bundleJarContentsUsingDummySigner, expectedMrsigner = dummyMrsigner)

        println("Using 'externalKey' signing, first with default signing material output location ...")
        testExternalSigning()
    }

    private fun testDummyKeySigning(
        bundleJarContentsUsingDummySigner: ByteArray,
        expectedMrsigner: SHA256Hash
    ) {
        // Switch back to using the dummy key but this time make it explicit in the config.
        updateBuildFile("signingType = privateKey", "signingType = dummyKey")
        runTaskAndAssertItsIncremental()
        assertThat(output).hasBinaryContent(bundleJarContentsUsingDummySigner)
        assertSignedEnclave(expectedMrsigner = expectedMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()
    }

    private fun testPrivateKeySigning() {
        val userSigningKey = Files.createTempFile(buildDir, "user_signing_key", ".pem").also {
            TestUtils.generateSigningKey(it)
        }
        val userMrsigner = calculateMrsigner(readSigningKey(userSigningKey))

        // Insert the config block for the current enclave mode and switch to the 'privateKey' signing type.
        val enclaveModeBlock = """${enclaveMode.name.lowercase()} {
                    |   signingType = privateKey
                    |   signingKey = file('$userSigningKey')
                    |}""".trimMargin()
        updateBuildFile("conclave {\n", "conclave {\n$enclaveModeBlock\n")
        runTaskAndAssertItsIncremental()
        assertSignedEnclave(expectedMrsigner = userMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()
    }

    // Delete the signed enclave file and make sure it's reproducible
    private fun testDeletingSignedEnclave(expectedMrsigner: SHA256Hash): ByteArray {
        val bundleJarContentsUsingDummySigner = output.readBytes()
        output.deleteExisting()
        runTaskAndAssertItsIncremental()
        assertThat(output).hasBinaryContent(bundleJarContentsUsingDummySigner)
        assertSignedEnclave(expectedMrsigner = expectedMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()
        return bundleJarContentsUsingDummySigner
    }

    // Modify the productID, which will invoke native-image for the second and final time for this test
    private fun testChangingEnclaveConfig(expectedMrsigner: SHA256Hash) {
        updateBuildFile("productID = 11", "productID = 12")
        updateBuildFile("revocationLevel = 12", "revocationLevel = 20")
        runTaskAndAssertItsIncremental()
        assertSignedEnclave(expectedMrsigner = expectedMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
    }

    private fun testExternalSigning() {
        val userSigningKey = Files.createTempFile(buildDir, "external_signing_key", ".pem").also {
            TestUtils.generateSigningKey(it)
        }
        val mrsigner = calculateMrsigner(readSigningKey(userSigningKey))

        val signatureFile = Files.createTempFile(buildDir, "signature", ".bin")
        val publicKeyFile = Files.createTempFile(buildDir, "signing_public_key", ".pem")

        execCommand(
            "openssl", "rsa",
            "-in", userSigningKey.absolutePathString(),
            "-pubout", "-out", publicKeyFile.absolutePathString()
        )

        // Now use 'externalKey' signing and sign the enclave manually
        updateBuildFile(
            "signingType = dummyKey",
            """signingType = externalKey
               signatureDate = new Date(1970, 0, 1)
               mrsignerSignature = file("$signatureFile")
               mrsignerPublicKey = file("$publicKeyFile")
            """.trimIndent()
        )

        // First generate the signing material and expect the default output location to be used
        val defaultSigningMaterialFile = buildDir / enclaveMode.name.lowercase() / "signing_material.bin"
        assertThat(defaultSigningMaterialFile).doesNotExist()
        runTaskAndAssertItsIncremental("generateEnclaveSigningMaterial$capitalisedMode")
        assertThat(defaultSigningMaterialFile).exists()
        assertUnsignedEnclaveHasntChanged()
        val signingMaterial = defaultSigningMaterialFile.readBytes()

        println("Using user provided location for signing material output...")
        val signingMaterialFile = Files.createTempFile(buildDir, "signing_material", ".bin")
        // Insert signingMaterial config
        updateBuildFile(
            "signingType = externalKey",
            "signingType = externalKey\nsigningMaterial = file('$signingMaterialFile')\n"
        )
        runTaskAndAssertItsIncremental("generateEnclaveSigningMaterial$capitalisedMode")
        assertUnsignedEnclaveHasntChanged()
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
        assertSignedEnclave(expectedMrsigner = mrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()
    }

    private fun assertSignedEnclave(expectedMrsigner: SHA256Hash, expectedProductID: Int, expectedRevocationLevel: Int) {
        checkBundleJarContents()
        val enclaveMetadata = getEnclaveMetadata(signedEnclave)
        assertThat(enclaveMetadata[body][IsvProdId].read()).isEqualTo(expectedProductID)
        assertThat(enclaveMetadata[body][IsvSvn].read()).isEqualTo(expectedRevocationLevel + 1)
        assertThat(SgxTypesKt.getMrsigner(enclaveMetadata[key])).isEqualTo(expectedMrsigner)
    }

    private fun assertUnsignedEnclaveHasntChanged() {
        assertThat(unsignedEnclave.getLastModifiedTime()).isEqualTo(originalUnsignedEnclaveModifiedTime)
    }

    private val unsignedEnclave: Path get() = enclaveModeBuildDir / "enclave.so"

    private val signedEnclave: Path get() = enclaveModeBuildDir / "enclave.signed.so"

    private fun checkBundleJarContents() {
        val signedEnclaveBytes = JarFile(output.toFile()).use { jar ->
            val bundlePath = "com/r3/conclave/enclave/user-bundles/com.test.enclave.TestEnclave/" +
                    "${enclaveMode.name.lowercase()}-graalvm.so"
            val entry = jar.assertEntryExists(bundlePath)
            jar.getInputStream(entry).use { it.readBytes() }
        }
        assertThat(signedEnclave).hasBinaryContent(signedEnclaveBytes)
    }
}

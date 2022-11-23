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
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readBytes

/**
 * This test is similar to [GramineEnclaveBundleJarTest] in that it tests the enclaveBundleJar task but when the
 * runtime type is Graal VM.
 */
class GraalVMEnclaveBundleJarTest : AbstractTaskTest(), TestWithSigning {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() {
        val capitalisedMode = enclaveMode.name.lowercase().replaceFirstChar(Char::titlecase)
        return "enclaveBundle${capitalisedMode}Jar"
    }

    override val output: Path get() {
        return buildDir / "libs" / "$projectName-bundle-${enclaveMode.name.lowercase()}.jar"
    }

    override val runDeletionAndReproducibilityTest: Boolean get() = false

    private lateinit var originalUnsignedEnclaveModifiedTime: FileTime

    @Test
    fun `single test run`() {
        // Perform the first run which will build the unsigned enclave so and then sign it with the random dummy key
        runTaskAndAssertItsIncremental()
        val dummyMrsigner = calculateMrsigner(readSigningKey(dummyKeyFile))

        assertEnclaveMetadata(expectedMrsigner = dummyMrsigner, expectedProductID = 11, expectedRevocationLevel = 12)

        println("Changing the productID and revocationLevel ...")
        // Then modify the productID, which will invoke native-image for the second and final time for this test
        updateBuildFile("productID = 11", "productID = 12")
        updateBuildFile("revocationLevel = 12", "revocationLevel = 20")
        runTaskAndAssertItsIncremental()
        assertEnclaveMetadata(expectedMrsigner = dummyMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)

        // Capture the modified time of the unsigned enclave file and make sure from now on it doesn't get rebuilt.
        originalUnsignedEnclaveModifiedTime = unsignedEnclave.getLastModifiedTime()

        println("Deleting the output signed enclave file ...")
        // Delete the signed enclave file and make sure it's reproducible
        val bundleJarContentsUsingDummySigner = output.readBytes()
        output.deleteExisting()
        runTaskAndAssertItsIncremental()
        assertThat(output).hasBinaryContent(bundleJarContentsUsingDummySigner)
        assertEnclaveMetadata(expectedMrsigner = dummyMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()

        val userSigningKey = Files.createTempFile(buildDir, "userSigningKey", null).also(TestUtils::generateSigningKey)
        val userMrsigner = calculateMrsigner(readSigningKey(userSigningKey))

        println("Using 'privateKey' signing ...")
        // Insert the config block for the current enclave mode and switch to the 'privateKey' signing type.
        val enclaveModeBlock = """${enclaveMode.name.lowercase()} {
                |   signingType = privateKey
                |   signingKey = file('$userSigningKey')
                |}""".trimMargin()
        updateBuildFile("conclave {\n", "conclave {\n$enclaveModeBlock\n")
        runTaskAndAssertItsIncremental()
        assertEnclaveMetadata(expectedMrsigner = userMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()

        println("Using 'dummyKey' signing ...")
        // Switch back to using the dummy key but this time make it explicit in the config.
        updateBuildFile("signingType = privateKey", "signingType = dummyKey")
        runTaskAndAssertItsIncremental()
        assertThat(output).hasBinaryContent(bundleJarContentsUsingDummySigner)
        assertEnclaveMetadata(expectedMrsigner = dummyMrsigner, expectedProductID = 12, expectedRevocationLevel = 20)
        assertUnsignedEnclaveHasntChanged()

        println("Using 'externalKey' signing ...")
        // Now use 'externalKey' signing and sign the enclave manually
        updateBuildFile("signingType = dummyKey", "signingType = externalKey")

    }

    private fun assertEnclaveMetadata(expectedMrsigner: SHA256Hash, expectedProductID: Int, expectedRevocationLevel: Int) {
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

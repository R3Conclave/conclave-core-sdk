package com.r3.conclave.integrationtests.general.tests.plugin

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AbstractModeTaskTest : AbstractConclaveTaskTest() {
    abstract val baseTaskName: String

    override val taskName: String get() = "$baseTaskName$enclaveMode"
    override val output: Path get() = enclaveModeBuildDir / outputName

    val enclaveModeBuildDir: Path get() = conclaveBuildDir / enclaveMode.lowercase()

    /**
     * Copy a prebuilt unsigned enclave into the project's build directory so that the task under test doesn't need
     * to rebuild a new GraalVM enclave, which is a lengthy process.
     */
    fun copyPrebuiltUnsignedGraalEnclave() {
        check(unsignedGraalEnclaveFile.exists()) {
            "A prebuilt unsigned enclave doesn't exist. Perhaps BuildUnsignedGraalEnclaveTest has failed?"
        }
        enclaveModeBuildDir.createDirectories()
        unsignedGraalEnclaveFile.copyTo(enclaveModeBuildDir / "enclave.so")
    }

    companion object {
        val enclaveOutputDir: Path = Path.of(System.getProperty("plugin-tests.enclave.output")).createDirectories()
        val unsignedGraalEnclaveFile: Path get() = enclaveOutputDir / "enclave.so"
    }
}

package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeEach
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
}

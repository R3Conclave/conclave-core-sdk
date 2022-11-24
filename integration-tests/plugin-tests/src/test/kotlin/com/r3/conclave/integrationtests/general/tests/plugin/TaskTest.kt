package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.name

interface TaskTest {
    val taskName: String
    val output: Path
    /**
     * If the task's output isn't stable then override with false, and explain why.
     */
    val isReproducible: Boolean get() = true

    val projectDir: Path

    val projectName: String get() = projectDir.name
    val buildGradleFile: Path get() = projectDir / "build.gradle"
    val buildDir: Path get() = projectDir / "build"
    val conclaveBuildDir: Path get() = buildDir / "conclave"
    val enclaveModeBuildDir: Path get() = conclaveBuildDir / TestUtils.enclaveMode.name.lowercase()
}

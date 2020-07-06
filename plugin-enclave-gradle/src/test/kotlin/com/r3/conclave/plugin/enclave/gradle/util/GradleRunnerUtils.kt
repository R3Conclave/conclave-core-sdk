package com.r3.conclave.plugin.enclave.gradle.util

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class GradleRunnerUtils {
    companion object {
        private val testGradleUserHome : String = System.getProperty("test.gradle.user.home")
        private val gradleVersion : String = System.getProperty("gradle.version")

        /**
         * GradleRunner is set withDebug(false) since it can cause tasks, such as the Copy task,
         * to misbehave and delete stale output directories after being considered UP_TO_DATE.
         */
        @JvmStatic
        fun gradleRunner(task: String, projectDirectory: Path): GradleRunner {
            return GradleRunner.create()
                    .withGradleVersion(gradleVersion)
                    .withDebug(false)
                    .withProjectDir(projectDirectory.toFile())
                    .withArguments(task, "--no-build-cache", "--stacktrace", "--info", "--gradle-user-home", testGradleUserHome)
                    .forwardOutput()
        }

        fun getProjectPath(path: String): Path = Paths.get(GradleRunnerUtils::class.java.classLoader.getResource(path)!!.toURI())

        fun clean(projectDirectory: Path) = File("$projectDirectory/.gradle").deleteRecursively()
    }
}
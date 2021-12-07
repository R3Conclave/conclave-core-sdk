package com.r3.conclave.init.gradle

import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class GradlePropertiesFile(val path: Path) {
    companion object {
        fun detect(
            gradleUserHome: Path? = System.getenv("GRADLE_USER_HOME")?.let(::Path),
            userHome: Path = System.getProperty("user.home").let(::Path),
        ): GradlePropertiesFile {
            return if (gradleUserHome != null) {
                GradlePropertiesFile(gradleUserHome / "gradle.properties")
            } else {
                GradlePropertiesFile(userHome / ".gradle" / "gradle.properties")
            }
        }
    }

    fun exists(): Boolean = path.exists()

    fun parse(): ConclaveProperties {
        with(Properties()) {
            path.inputStream().use(::load)

            return ConclaveProperties(
                getProperty("conclaveRepo")?.let(::Path),
                getProperty("conclaveVersion")
            )
        }
    }
}

data class ConclaveProperties(val conclaveRepo: Path?, val conclaveVersion: String?)

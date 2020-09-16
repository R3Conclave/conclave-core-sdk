package com.r3.conclave.plugin.enclave.gradle.os

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class OSDependentToolsTest {

    private val conclaveDependenciesDirectory = "x"
    private val sdkVersion = "0.0"
    private var linux = LinuxDependentTools(conclaveDependenciesDirectory)
    private val macOS = MacOSDependentTools(conclaveDependenciesDirectory)
    private val windows = WindowsDependentTools(conclaveDependenciesDirectory)

    @Test
    fun getToolsArtifactsLinux() {
        val toolsDependenciesIDs = linux.getToolsDependenciesIDs(sdkVersion)
        assertThat(toolsDependenciesIDs).containsExactlyInAnyOrder(
                "com.r3.conclave:native-binutils:$sdkVersion", "com.r3.conclave:native-sign-tool:$sdkVersion"
        )
    }

    @Test
    fun getToolsArtifactsMacOS() {
        val toolsDependenciesIDs = macOS.getToolsDependenciesIDs(sdkVersion)
        assertThat(toolsDependenciesIDs).containsExactlyInAnyOrder(
                "com.r3.conclave:macos-binutils:$sdkVersion", "com.r3.conclave:native-binutils:$sdkVersion", "com.r3.conclave:macos-sign-tool:$sdkVersion"
        )
    }

    @Test
    fun getToolsArtifactsWindows() {
        val toolsDependenciesIDs = windows.getToolsDependenciesIDs(sdkVersion)
        assertThat(toolsDependenciesIDs).containsExactlyInAnyOrder(
                "com.r3.conclave:native-binutils:$sdkVersion", "com.r3.conclave:native-sign-tool:$sdkVersion"
        )
    }

    @Test
    fun getLdFileLinux() {
        val ldFile = linux.getLdFile()
        assertThat(ldFile).isEqualTo("$conclaveDependenciesDirectory/binutils/ld")
    }

    @Test
    fun getLdFileMacOS() {
        val ldFile = macOS.getLdFile()
        assertThat(ldFile).isEqualTo("$conclaveDependenciesDirectory/binutils/macos/ld-new")
    }

    @Test
    fun getLdFileWindows() {
        val ldFile = windows.getLdFile()
        assertThat(ldFile).isEqualTo("$conclaveDependenciesDirectory/binutils/ld.exe")
    }

    @Test
    fun getSgxSignLinux() {
        val sgxSign = linux.getSgxSign()
        assertThat(sgxSign).isEqualTo("$conclaveDependenciesDirectory/sign-tool/sgx_sign")
    }

    @Test
    fun getSgxSignMacOS() {
        val sgxSign = macOS.getSgxSign()
        assertThat(sgxSign).isEqualTo(("$conclaveDependenciesDirectory/sign-tool/macos/sgx_sign"))
    }

    @Test
    fun getSgxSignWindows() {
        val sgxSign = windows.getSgxSign()
        assertThat(sgxSign).isEqualTo("$conclaveDependenciesDirectory/sign-tool/sgx_sign.exe")
    }

    @Test
    fun getOpensslFileLinux() {
        val opensslFile = linux.getOpensslFile()
        assertThat(opensslFile).isEqualTo("$conclaveDependenciesDirectory/binutils/opensslw")
    }

    @Test
    fun getOpensslFileMacOS() {
        val opensslFile = macOS.getOpensslFile()
        assertThat(opensslFile).isEqualTo("/usr/local/bin/openssl")
    }

    @Test
    fun getOpensslFileWindows() {
        val opensslFile = windows.getOpensslFile()
        assertThat(opensslFile).isEqualTo("$conclaveDependenciesDirectory/binutils/opensslw.exe")
    }
}
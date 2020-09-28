package com.r3.conclave.plugin.enclave.gradle.os

interface OSDependentTools {
    fun getToolsDependenciesIDs(sdkVersion: String): List<String> {
        return listOf(
                "com.r3.conclave:native-binutils:$sdkVersion",
                "com.r3.conclave:native-sign-tool:$sdkVersion"
        )
    }

    fun getLdFile(): String

    fun getNativeImageLdFile(): String

    fun getSgxSign(): String

    fun getExecutable(name: String): String {
        return name
    }
}

class LinuxDependentTools(private val conclaveDependenciesDirectory: String) : OSDependentTools {
    override fun getLdFile(): String {
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("ld")}"
    }

    override fun getNativeImageLdFile(): String {
        return getLdFile()
    }

    override fun getSgxSign(): String {
        return "$conclaveDependenciesDirectory/sign-tool/${getExecutable("sgx_sign")}"
    }
}

class MacOSDependentTools(private val conclaveDependenciesDirectory: String) : OSDependentTools {
    override fun getToolsDependenciesIDs(sdkVersion: String): List<String> {
        return listOf(
                "com.r3.conclave:macos-binutils:$sdkVersion",
                "com.r3.conclave:native-binutils:$sdkVersion",
                "com.r3.conclave:macos-sign-tool:$sdkVersion"
        )
    }

    override fun getLdFile(): String {
        return "$conclaveDependenciesDirectory/binutils/macos/ld-new"
    }

    override fun getNativeImageLdFile(): String {
        // This runs in a Linux docker image on Mac so use the Linux ld
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("ld")}"
    }

    override fun getSgxSign(): String {
        return "$conclaveDependenciesDirectory/sign-tool/macos/sgx_sign"
    }
}

class WindowsDependentTools(private val conclaveDependenciesDirectory: String) : OSDependentTools {
    override fun getLdFile(): String {
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("ld")}"
    }

    override fun getNativeImageLdFile(): String {
        return getLdFile()
    }

    override fun getSgxSign(): String {
        return "$conclaveDependenciesDirectory/sign-tool/${getExecutable("sgx_sign")}"
    }

    override fun getExecutable(name: String): String {
        return "$name.exe"
    }
}
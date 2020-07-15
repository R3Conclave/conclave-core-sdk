package com.r3.conclave.plugin.enclave.gradle.os

interface OSDependentTools {
    fun getToolsDependenciesIDs(sdkVersion: String): Array<String> {
        return arrayOf("com.r3.conclave:native-binutils:$sdkVersion", "com.r3.conclave:native-sign-tool:$sdkVersion")
    }

    fun getLdFile(): String

    fun getSgxSign(): String

    fun getOpensslFile(): String

    fun getExecutable(name: String): String {
        return name
    }
}

class LinuxDependentTools(private val conclaveDependenciesDirectory: String) : OSDependentTools {
    override fun getLdFile(): String {
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("ld")}"
    }

    override fun getSgxSign(): String {
        return "$conclaveDependenciesDirectory/sign-tool/${getExecutable("sgx_sign")}"
    }

    override fun getOpensslFile(): String {
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("opensslw")}"
    }
}

class MacOSDependentTools(private val conclaveDependenciesDirectory: String) : OSDependentTools {
    override fun getToolsDependenciesIDs(sdkVersion: String): Array<String> {
        return arrayOf("com.r3.conclave:macos-binutils:$sdkVersion", "com.r3.conclave:macos-sign-tool:$sdkVersion")
    }

    override fun getLdFile(): String {
        return "$conclaveDependenciesDirectory/binutils/macos/ld-new"
    }

    override fun getSgxSign(): String {
        return "$conclaveDependenciesDirectory/sign-tool/macos/sgx_sign"
    }

    override fun getOpensslFile(): String {
        return "/usr/local/bin/openssl"
    }
}

class WindowsDependentTools(private val conclaveDependenciesDirectory: String) : OSDependentTools {
    override fun getLdFile(): String {
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("ld")}"
    }

    override fun getSgxSign(): String {
        return "$conclaveDependenciesDirectory/sign-tool/${getExecutable("sgx_sign")}"
    }

    override fun getOpensslFile(): String {
        return "$conclaveDependenciesDirectory/binutils/${getExecutable("opensslw")}"
    }

    override fun getExecutable(name: String): String {
        return "$name.exe"
    }
}
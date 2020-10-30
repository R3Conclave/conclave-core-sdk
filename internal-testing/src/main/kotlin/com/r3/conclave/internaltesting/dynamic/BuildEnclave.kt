package com.r3.conclave.internaltesting.dynamic

import java.io.File

enum class EnclaveType {
    Simulation,
    Debug,
    Release
}

object BuildEnclave {
    fun getPartialEnclaveResourcePath(type: EnclaveType): String {
        return "/com/r3/conclave/partial-enclave/$type/jvm_enclave_avian"
    }

    fun copyAppJar(enclaveJar: Cached<File>): Cached<File> {
        return enclaveJar.mapFile("app.jar") { output, jar ->
            jar.copyTo(output)
        }
    }

    fun buildJarObject(enclaveJar: Cached<File>): Cached<File> {
        return copyAppJar(enclaveJar).mapFile("app.jar.o") { output, jar ->
            ProcessRunner.runProcess(buildJarObjectCommandLine(output), jar.parentFile)
        }
    }

    fun buildEnclave(type: EnclaveType, enclaveJar: Cached<File>): Cached<File> {
        val extractPartialEnclave = ExtractResource.extractResource(javaClass, getPartialEnclaveResourcePath(type), "r--r--r--")
        val buildJarObject = buildJarObject(enclaveJar)
        return extractPartialEnclave.combineFile(buildJarObject, "enclave.so") { output, partialEnclave, jarObject ->
            ProcessRunner.runProcess(linkEnclaveCommandLine(
                    jarObject,
                    partialEnclave,
                    output
            ), output.parentFile)
        }
    }

    fun buildJarObjectCommandLine(output: File): List<String> {
        return listOf("/usr/bin/env", "objcopy",
                "-I", "binary",
                "-O", "elf64-x86-64",
                "-B", "i386",
                "app.jar", output.absolutePath
        )
    }

    fun linkEnclaveCommandLine(enclaveJarO: File, partialEnclave: File, outputEnclave: File): List<String> {
        return listOf("/usr/bin/env", "ld",
                "-pie", "--entry=enclave_entry",
                "-Bstatic", "-Bsymbolic", "--no-undefined", "--export-dynamic", "--defsym=__ImageBase=0",
                "--defsym=__DeadlockTimeout=10", 
                "-o", outputEnclave.absolutePath,
                partialEnclave.absolutePath,
                enclaveJarO.absolutePath
        )
    }
}

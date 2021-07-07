package com.r3.conclave.internaltesting.dynamic

import java.io.File

enum class EnclaveType {
    Simulation,
    Debug,
    Release
}

object BuildEnclave {

    private fun copyAppJar(enclaveJar: Cached<File>): Cached<File> {
        return enclaveJar.mapFile("app.jar") { output, jar ->
            jar.copyTo(output)
        }
    }

    private fun buildJarObject(enclaveJar: Cached<File>): Cached<File> {
        return copyAppJar(enclaveJar).mapFile("app.jar.o") { output, jar ->
            ProcessRunner.runProcess(buildJarObjectCommandLine(output), jar.parentFile)
        }
    }

    fun buildEnclave(type: EnclaveType, enclaveJar: Cached<File>): Cached<File> {
        val buildJarObject = buildJarObject(enclaveJar)
        var pureObject = Cached.Pure(buildJarObject)
        return pureObject.combineFile(buildJarObject, "enclave.so") { output, jarObject ->
            ProcessRunner.runProcess(
                linkEnclaveCommandLine(
                    jarObject,
                    output
                ), output.parentFile
            )
        }
    }

    private fun buildJarObjectCommandLine(output: File): List<String> {
        return listOf(
            "/usr/bin/env", "objcopy",
            "-I", "binary",
            "-O", "elf64-x86-64",
            "-B", "i386",
            "app.jar", output.absolutePath
        )
    }

    private fun linkEnclaveCommandLine(enclaveJarO: File, outputEnclave: File): List<String> {
        return listOf(
            "/usr/bin/env", "ld",
            "-pie", "--entry=enclave_entry",
            "-Bstatic", "-Bsymbolic", "--no-undefined", "--export-dynamic", "--defsym=__ImageBase=0",
            "--defsym=__DeadlockTimeout=10",
            "-o", outputEnclave.absolutePath,
            enclaveJarO.absolutePath
        )
    }
}

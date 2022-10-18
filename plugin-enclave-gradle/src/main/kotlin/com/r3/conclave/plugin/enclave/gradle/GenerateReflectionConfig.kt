package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.nio.file.Files
import javax.inject.Inject

open class GenerateReflectionConfig @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {

        private val ATTESTATION_CLASSES = listOf(
            "com.r3.conclave.common.internal.attestation.SignedTcbInfo",
            "com.r3.conclave.common.internal.attestation.TcbInfo",
            "com.r3.conclave.common.internal.attestation.TcbLevel",
            "com.r3.conclave.common.internal.attestation.Tcb",
            "com.r3.conclave.common.internal.attestation.SignedEnclaveIdentity",
            "com.r3.conclave.common.internal.attestation.EnclaveIdentity",
            "com.r3.conclave.common.internal.attestation.EnclaveTcbLevel",
            "com.r3.conclave.common.internal.attestation.EnclaveTcb"
        )

        val DEFAULT_CLASSES = ATTESTATION_CLASSES

        @JvmStatic
        fun generateContent(classNames: List<String>): String {
            val builder = StringBuilder("[${System.lineSeparator()}")
            classNames.forEach { className ->
                builder.append("""
                    {
                        "name" : "$className",
                        "allDeclaredConstructors" : true,
                        "allPublicConstructors" : true,
                        "allDeclaredMethods" : true,
                        "allPublicMethods" : true,
                        "allDeclaredClasses" : true,
                        "allPublicClasses" : true,
                        "allPublicFields" : true,
                        "allDeclaredFields" : true
                    },
                """.trimIndent())
            }
            val lastIndexOfComma = builder.lastIndexOf(",")
            builder.replace(lastIndexOfComma, lastIndexOfComma + 1, "${System.lineSeparator()}]")
            return builder.toString()
        }
    }

    @Input
    val enclaveClass: Property<String> = objects.property(String::class.javaObjectType)

    @OutputFile
    val reflectionConfig: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val content = generateContent(DEFAULT_CLASSES + enclaveClass.get())
        Files.write(reflectionConfig.get().asFile.toPath(), content.toByteArray())
    }
}

package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.nio.file.Files
import javax.inject.Inject

open class GenerateEnclaveConfig @Inject constructor(objects: ObjectFactory, private val buildType: BuildType) : ConclaveTask() {
    @get:Input
    val productID: Property<Int> = objects.property(Int::class.java)
    @get:Input
    val revocationLevel: Property<Int> = objects.property(Int::class.java)
    @get:Input
    val maxHeapSize: Property<String> = objects.property(String::class.java)

    @get:OutputFile
    val outputConfigFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val productID = productID.get()
        if (productID < 0 || productID > 65535) {
            throw InvalidUserDataException("Product ID is not an unsigned 16 bit number")
        }

        val revocationLevel = revocationLevel.get()
        if (revocationLevel < 0 || revocationLevel > 65534) {  // Revocation level is one less than the ISVSVN
            throw InvalidUserDataException("Revocation level is invalid")
        }

        val maxHeapSizeBytes = maxHeapSize.get().let {
            when {
                it.endsWith("G", ignoreCase = true) -> it.dropLast(1).toLong() shl 30
                it.endsWith("M", ignoreCase = true) -> it.dropLast(1).toLong() shl 20
                it.endsWith("K", ignoreCase = true) -> it.dropLast(1).toLong() shl 10
                else -> it.toLong()
            }
        }

        val disableDebug = if (buildType == BuildType.Release) 1 else 0

        val content = """
            <EnclaveConfiguration>
                <ProdID>$productID</ProdID>
                <ISVSVN>${revocationLevel + 1}</ISVSVN>
                <StackMaxSize>0x280000</StackMaxSize>
                <HeapMaxSize>0x${maxHeapSizeBytes.toString(16)}</HeapMaxSize>
                <TCSNum>10</TCSNum>
                <TCSPolicy>1</TCSPolicy>
                <DisableDebug>$disableDebug</DisableDebug>
                <MiscSelect>0</MiscSelect>
                <MiscMask>0xFFFFFFFF</MiscMask>
            </EnclaveConfiguration>
        """.trimIndent()

        Files.write(outputConfigFile.get().asFile.toPath(), content.toByteArray())
    }
}

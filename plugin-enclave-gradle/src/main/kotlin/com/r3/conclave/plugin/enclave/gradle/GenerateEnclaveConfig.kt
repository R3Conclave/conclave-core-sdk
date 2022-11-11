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
    val productID: Property<Int> = objects.intProperty()
    @get:Input
    val revocationLevel: Property<Int> = objects.intProperty()
    @get:Input
    val maxStackSize: Property<String> = objects.stringProperty()
    @get:Input
    val maxHeapSize: Property<String> = objects.stringProperty()
    @get:Input
    val tcsNum: Property<Int> = objects.intProperty()

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

        val maxStackSizeBytes = maxStackSize.get().toSizeBytes()
        val maxHeapSizeBytes = maxHeapSize.get().toSizeBytes()

        val disableDebug = if (buildType == BuildType.Release) 1 else 0

        val content = """
            <EnclaveConfiguration>
                <ProdID>$productID</ProdID>
                <ISVSVN>${revocationLevel + 1}</ISVSVN>
                <StackMaxSize>0x${maxStackSizeBytes.toString(16)}</StackMaxSize>
                <HeapMaxSize>0x${maxHeapSizeBytes.toString(16)}</HeapMaxSize>
                <TCSNum>${tcsNum.get()}</TCSNum>
                <TCSPolicy>1</TCSPolicy>
                <DisableDebug>$disableDebug</DisableDebug>
                <MiscSelect>0</MiscSelect>
                <MiscMask>0xFFFFFFFF</MiscMask>
            </EnclaveConfiguration>
        """.trimIndent()

        Files.write(outputConfigFile.get().asFile.toPath(), content.toByteArray())
    }
}

package com.r3.sgx.plugin.enclave

import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.util.*
import javax.inject.Inject

open class EnclaveExtension @Inject constructor(
        objects: ObjectFactory,
        defaultConfiguration: RegularFile,
        defaultShouldUseDummyKey: Boolean,
        defaultMrsignerPublicKey: RegularFile,
        defaultMrsignerSignature: RegularFile,
        defaultSignatureDate: Date
) {
    val configuration: RegularFileProperty = objects.fileProperty().convention(defaultConfiguration)
    val shouldUseDummyKey: Property<Boolean> =
            objects.property(Boolean::class.javaObjectType).convention(defaultShouldUseDummyKey)
    val mrsignerPublicKey: RegularFileProperty = objects.fileProperty().convention(defaultMrsignerPublicKey)
    val mrsignerSignature: RegularFileProperty = objects.fileProperty().convention(defaultMrsignerSignature)
    val signatureDate: Property<Date> =
            objects.property(Date::class.javaObjectType).convention(defaultSignatureDate)
}

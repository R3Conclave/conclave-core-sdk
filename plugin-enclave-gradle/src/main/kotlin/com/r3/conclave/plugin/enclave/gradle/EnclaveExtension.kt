package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.util.*
import javax.inject.Inject

open class EnclaveExtension @Inject constructor(objects: ObjectFactory) {
    val configuration: RegularFileProperty = objects.fileProperty()
    val shouldUseDummyKey: Property<Boolean> = objects.property(Boolean::class.java)
    val mrsignerPublicKey: RegularFileProperty = objects.fileProperty()
    val mrsignerSignature: RegularFileProperty = objects.fileProperty()
    val signatureDate: Property<Date> = objects.property(Date::class.java)
}

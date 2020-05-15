package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.util.*
import javax.inject.Inject

open class EnclaveExtension @Inject constructor(objects: ObjectFactory) {
    // Define constants to make it easier for the configurer to select a signing type
    val dummyKey = SigningType.DummyKey
    val privateKey = SigningType.PrivateKey
    val externalKey = SigningType.ExternalKey

    val signingType: Property<SigningType> = objects.property(SigningType::class.java)
    val mrsignerPublicKey: RegularFileProperty = objects.fileProperty()
    val mrsignerSignature: RegularFileProperty = objects.fileProperty()
    val signatureDate: Property<Date> = objects.property(Date::class.java)
    val signingMaterial: RegularFileProperty = objects.fileProperty()
    val signingKey: RegularFileProperty = objects.fileProperty()
}

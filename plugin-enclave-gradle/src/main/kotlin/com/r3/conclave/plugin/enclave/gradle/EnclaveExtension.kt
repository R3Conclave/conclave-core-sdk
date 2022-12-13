package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.EnclaveMode
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.util.*
import javax.inject.Inject

open class EnclaveExtension @Inject constructor(
    objects: ObjectFactory,
    projectLayout: ProjectLayout,
    val enclaveMode: EnclaveMode
) {
    // Define constants to make it easier for the configurer to select a signing type
    @Suppress("unused")
    val dummyKey = SigningType.DummyKey
    @Suppress("unused")
    val privateKey = SigningType.PrivateKey
    @Suppress("unused")
    val externalKey = SigningType.ExternalKey

    val signingType: Property<SigningType> = objects.property(SigningType::class.java).convention(
        // Release mode defaults to external signing, whilst the other two default to the dummy key.
        if (enclaveMode == EnclaveMode.RELEASE) SigningType.ExternalKey else SigningType.DummyKey
    )
    val mrsignerPublicKey: RegularFileProperty = objects.fileProperty()
    val mrsignerSignature: RegularFileProperty = objects.fileProperty()
    val signatureDate: Property<Date> = objects.property(Date::class.java)
    val signingMaterial: RegularFileProperty = objects.fileProperty().convention(
        projectLayout.buildDirectory.file("enclave/${enclaveMode.name.lowercase()}/signing_material.bin")
    )
    val signingKey: RegularFileProperty = objects.fileProperty()
}

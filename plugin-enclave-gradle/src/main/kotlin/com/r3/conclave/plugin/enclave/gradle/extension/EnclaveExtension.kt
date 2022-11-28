package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.SigningType
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import java.util.*
import javax.inject.Inject

open class EnclaveExtension @Inject constructor(
    objects: ObjectFactory,
    private val buildType: BuildType,
    private val projectLayout: ProjectLayout
) {
    @get:Input
    val signingType: Property<SigningType> = objects.property(SigningType::class.java).convention(
        // Release mode defaults to external signing, whilst the other two default to the dummy key.
        if (buildType == BuildType.Release) SigningType.ExternalKey else SigningType.DummyKey
    )
    @get:InputFile
    @get:Optional
    val mrsignerPublicKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    val mrsignerSignature: RegularFileProperty = objects.fileProperty()

    @get:Input
    @get:Optional
    val signatureDate: Property<Date> = objects.property(Date::class.java)

    @get:InputFile
    @get:Optional
    val signingMaterial: RegularFileProperty = objects.fileProperty()

    // In theory it should have been possible to add .convention() to signingMaterial above with the default, but
    // it doesn't work as Gradle expects the file to exist. So instead we create this ready-only Provider which
    // should be used by tasks instead.
    val signingMaterialWithDefault: Provider<RegularFile>
        @Internal
        get() = signingMaterial.orElse(
            projectLayout.buildDirectory.file("enclave/${buildType.name.lowercase()}/signing_material.bin")
        )

    @get:InputFile
    @get:Optional
    val signingKey: RegularFileProperty = objects.fileProperty()

    // Define constants to make it easier for the configurer to select a signing type
    @Suppress("unused")
    @get:Internal
    val dummyKey = SigningType.DummyKey
    @Suppress("unused")
    @get:Internal
    val privateKey = SigningType.PrivateKey
    @Suppress("unused")
    @get:Internal
    val externalKey = SigningType.ExternalKey
}

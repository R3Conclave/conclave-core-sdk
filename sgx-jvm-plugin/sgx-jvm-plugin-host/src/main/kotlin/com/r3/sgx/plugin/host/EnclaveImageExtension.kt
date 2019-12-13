package com.r3.sgx.plugin.host

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.*
import javax.inject.Inject

@Suppress("MemberVisibilityCanBePrivate")
open class EnclaveImageExtension @Inject constructor(
    objects: ObjectFactory,
    registryCredentials: DockerRegistryCredentials,
    _baseImageName: String,
    _baseTag: String,
    _publishImageName: Provider<String>,
    _publishTag: String,
    _testGrpcPort: Int,
    _testStartTimeout: Int
) {
    val repositoryUrl: Provider<String> = registryCredentials.url
    val enclaveObject: RegularFileProperty = objects.fileProperty()
    val baseImageName: Property<String> = objects.property(String::class.java).convention(_baseImageName)
    val baseTag: Property<String> = objects.property(String::class.java).convention(_baseTag)
    val publishImageName: Property<String> = objects.property(String::class.java).convention(_publishImageName)
    val publishTag: Property<String> = objects.property(String::class.java).convention(_publishTag)
    val testing: EnclaveContainer = objects.newInstance(EnclaveContainer::class.java, _testGrpcPort, _testStartTimeout)

    fun testing(action: Action<in EnclaveContainer>) {
        action.execute(testing)
    }

    val publishableName: Provider<String> = repositoryUrl.flatMap { url ->
        publishImageName.map { name -> "$url/$name" }
    }

    /**
     * We can't map a [SetProperty] that contains any empty property elements,
     * and so we keep two sets: one with [publishTag] and one without.
     */
    private val latestTag: Provider<Set<String>> = objects.emptySetOf<String>().also {
        it.add(DEFAULT_TAG)
    }
    private val tags: Provider<Set<String>> = objects.emptySetOf<String>().also {
        it.addAll(latestTag)
        it.add(publishTag)
    }
    fun fullImageNamesFor(baseName: Provider<String>): Provider<Set<String>> = baseName.flatMap { name ->
        (if (publishTag.isPresent) tags else latestTag).map {
            it.map { tag -> "$name:$tag" }.toSet()
        }
    }
}

@Suppress("MemberVisibilityCanBePrivate")
open class EnclaveContainer @Inject constructor (
    objects: ObjectFactory,
    _grpcPort: Int,
    _startTimeout: Int
) {
    val grpcPort: Property<Int> = objects.property(Int::class.javaObjectType).convention(_grpcPort)
    val startTimeout: Property<Int> = objects.property(Int::class.javaObjectType).convention(_startTimeout)
    val configFile: RegularFileProperty = objects.fileProperty()
    val removeOnExit: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)

    val portBindings: Provider<List<String>> = grpcPort.map { port ->
        listOf("$port:$GRPC_PORT")
    }
}

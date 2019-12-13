package com.r3.sgx.endtoendtest

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.DaemonSetList
import io.fabric8.kubernetes.api.model.apps.DoneableDaemonSet
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.base.HasMetadataOperation
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.util.*

/**
 * Convenience methods to set up an end to end test environment.
 */
object KubernetesClusterSetup {
    private val log = LoggerFactory.getLogger(KubernetesClusterSetup::class.java)
    /**
     * Set up docker registry credentials in relevant namespaces.
     */
    fun KubernetesClientRule.setupCredentials() {
        val deployedSecrets = client.secrets().list().items.groupBy { it.metadata.name }
        val secretsToDeploy = mapOf(
                "registry-secret" to listOf(testNamespaceName, "kube-system")
       )
        for ((secretName, namespaces) in secretsToDeploy) {
            for (namespace in namespaces) {
                val secrets = deployedSecrets[secretName] ?: throw IllegalStateException("Cannot find secret $secretName")
                if (secrets.any { it.metadata.namespace == namespace }) {
                    // Already deployed
                    continue
                }
                log.info("Deploying secret $secretName to namespace $namespace")
                val secretToUpload = SecretBuilder(secrets.first())
                        .withMetadata(ObjectMetaBuilder()
                                .withName(secretName)
                                .withNamespace(namespace)
                                .build())
                        .build()
                client.secrets().createOrReplace(secretToUpload)
            }
        }
    }

    /**
     * Deploy the SGX device plugin.
     */
    fun KubernetesClientRule.deploySgxPlugin() {
        val betaDaemonSets = BetaDaemonSetOperationsImpl(httpClient, client.configuration, "kube-system")
        val daemonSet = Yaml().loadAs(javaClass.classLoader.getResourceAsStream("sgx-device-plugin-daemonset.yml"), DaemonSet::class.java)

        val existing = client.apps().daemonSets().inNamespace("kube-system").list().items.firstOrNull {
            it.metadata.name == daemonSet.metadata.name
        }
        if (existing == null) {
            betaDaemonSets.create(daemonSet)
        }

        addDiagnosticTarget(
                podNameRegex = "sgx-device-plugin-daemonset.*",
                containerNameRegex = "kubernetes-sgx-device-plugin",
                namespace = "kube-system"
        )

    }

    fun KubernetesClientRule.deployEnclaveletHost() {
        deployConfig("enclavelet-host-config", "enclavelet-host-config.yml", "config.yml")
        client.inNamespace(testNamespaceName).load(
                javaClass.classLoader.getResourceAsStream("enclavelet-host-deployment.yml")
        ).createOrReplace()
        addDiagnosticTarget(
                podNameRegex = "enclavelet-host-deployment.*",
                containerNameRegex = "enclavelet-host"
        )
        addDiagnosticTarget(
                podNameRegex = "enclavelet-host-deployment.*",
                containerNameRegex = "aesmd"
        )
        client.services().inNamespace(testNamespaceName).load(
                javaClass.classLoader.getResourceAsStream("enclavelet-host-service.yml")
        ).create()
        waitForPodReady("enclavelet-host-deployment.*")
    }

    private fun KubernetesClientRule.deployConfig(name: String, resourcePath: String, mountedFilename: String) {
        val configContents = javaClass.classLoader.getResourceAsStream(resourcePath).readBytes()
        client.inNamespace(testNamespaceName).configMaps().createOrReplace(
                ConfigMapBuilder()
                        .withNewMetadata().withName(name).endMetadata()
                        .addToData(mountedFilename, String(configContents)).build()
        )
    }

    private fun KubernetesClientRule.waitForPodReady(podRegexString: String) {
        val podRegex = podRegexString.toRegex()
        while (true) {
            val pod = client.inNamespace(testNamespaceName).pods().list().items.firstOrNull {
                it.metadata.name.matches(podRegex)
            }
            if (pod != null && pod.status.conditions.any { it.type == "Ready"  && it.status == "True"} ) {
                break
            }
            log.warn("$podRegexString isn't ready yet")
            Thread.sleep(1000)
        }
    }
}

/**
 * A class allowing access to the beta API that supports device plugins. This is needed because the fabric8 kubernetes
 * library doesn't always respect the apiGroup arguments and tends to default it internally.
 */
private class BetaDaemonSetOperationsImpl(
        client: OkHttpClient, config: Config, namespace: String
) :
        HasMetadataOperation<DaemonSet, DaemonSetList, DoneableDaemonSet, Resource<DaemonSet, DoneableDaemonSet>>(
                client,
                config,
                "extensions",
                "v1beta1",
                "daemonsets",
                namespace,
                null,
                true,
                null,
                null,
                false,
                -1,
                TreeMap(), TreeMap(), TreeMap(), TreeMap(), TreeMap()
        )

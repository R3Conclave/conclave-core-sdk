package com.r3.sgx.endtoendtest

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.utils.HttpClientUtils
import okhttp3.OkHttpClient
import org.junit.rules.ExternalResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides a JUnit rule that connects to a kubernetes cluster. It also provides a way to add "diagnostic targets" that
 * will be scanned for logs on test failure if [KubernetesDiagnosticRule] is used.
 */
class KubernetesClientRule(val shouldCreateFreshNamespace: Boolean) : ExternalResource() {
    lateinit var testNamespaceName: String
    lateinit var client: NamespacedKubernetesClient
    lateinit var httpClient: OkHttpClient
    lateinit var namespace: Namespace
    lateinit var config: Config
    val diagnosticTargets = HashSet<DiagnosticTarget>()

    private companion object {
        val log: Logger = LoggerFactory.getLogger(KubernetesClientRule::class.java)
    }

    override fun before() {
        config = ConfigBuilder()
                .withMasterUrl("localhost:8080")
                .build()
        httpClient = HttpClientUtils.createHttpClient(config)

        client = DefaultKubernetesClient(httpClient, config)
        testNamespaceName = "test"
        namespace = NamespaceBuilder().withMetadata(ObjectMetaBuilder().withName(testNamespaceName).build()).build()
        if (shouldCreateFreshNamespace) {
            createFreshNamespace()
        }
    }

    override fun after() {
        client.close()
    }

    fun createFreshNamespace() {
        val namespaces = client.namespaces().list()
        val existingNamespace = namespaces.items.firstOrNull { it.metadata.name == testNamespaceName }
        if (existingNamespace != null && existingNamespace.status.phase != "Terminating") {
            log.warn("Namespace leaked from previous run, cleaning up...")
            client.namespaces().delete(namespace)
        }
        do {
            log.info("Waiting for namespace to go away...")
            Thread.sleep(500)
        } while (client.namespaces().list().items.any { it.metadata.name == testNamespaceName })
        client.namespaces().create(namespace)
    }

    /**
     * Add a diagnostic target to gather logs from on test failure.
     * @param podNameRegex A regular expression matching the pod to be inspected.
     * @param containerNameRegex A regular expression matching the container to be inspected in the pod.
     * @param namespace The namespace to search in.
     */
    fun addDiagnosticTarget(podNameRegex: String, containerNameRegex: String, namespace: String = testNamespaceName) {
        diagnosticTargets.add(DiagnosticTarget(podNameRegex, containerNameRegex, namespace))
    }
}

class DiagnosticTarget(
        val podNameRegex: String,
        val containerNameRegex: String,
        val namespace: String
)

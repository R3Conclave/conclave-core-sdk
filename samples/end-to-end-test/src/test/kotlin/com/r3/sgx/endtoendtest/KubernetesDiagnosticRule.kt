package com.r3.sgx.endtoendtest

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A rule that gathers diagnostics from registered target containers in case of a test failure.
 * @param kubernetesClientRule The kubernetes client rule that specifies the diagnostic targets.
 */
class KubernetesDiagnosticRule(private val kubernetesClientRule: KubernetesClientRule) : TestWatcher() {
    private companion object {
        val log: Logger = LoggerFactory.getLogger(KubernetesDiagnosticRule::class.java)
    }

    override fun failed(e: Throwable, description: Description) {
        log.info("Test failure, gathering diagnostics...")
        val numberOfTries = 5
        for (i in 1 .. numberOfTries) {
            try {
                for (target in kubernetesClientRule.diagnosticTargets) {
                    val pods = kubernetesClientRule.client.pods().inNamespace(target.namespace).list().items
                    val pod = pods.firstOrNull {
                        it.metadata.name.matches(target.podNameRegex.toRegex())
                    }
                    if (pod == null) {
                        log.warn("Cannot find requested pod ${target.podNameRegex} to gather diagnostics from. " +
                                "Potential pods: ${pods.map { it.metadata.name }}"
                        )
                        continue
                    }
                    val containers = kubernetesClientRule.client.pods()
                            .inNamespace(target.namespace)
                            .withName(pod.metadata.name)
                            .get()
                            .spec.containers
                    val container = containers.firstOrNull {
                        it.name.matches(target.containerNameRegex.toRegex())
                    }
                    if (container == null) {
                        log.warn("Cannot find requested container ${target.containerNameRegex} in pod " +
                                "${pod.metadata.name} to gather diagnostics from. Potential containers: " +
                                "${containers.map { it.name }}"
                        )
                        continue
                    }
                    log.info("Dumping logs from ${pod.metadata.name}:${container.name}")
                    kubernetesClientRule.client.pods()
                            .inNamespace(target.namespace)
                            .withName(pod.metadata.name)
                            .inContainer(container.name).logReader.forEachLine { logLine ->
                        println("${container.name}> $logLine")
                    }
                }
                return
            } catch (throwable: Throwable) {
                log.error("Error while gathering diagnostics", throwable)
                Thread.sleep(1000)
            }
        }
    }
}

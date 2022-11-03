package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.PluginUtils.ENCLAVE_BUNDLES_PATH
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_BUNDLE_NAME
import com.r3.conclave.common.internal.PluginUtils.GRAALVM_BUNDLE_NAME
import io.github.classgraph.ClassGraph
import java.net.URL
import java.util.regex.Pattern

/**
 * A scanner of enclaves. By default the class path is scanned, but this can be changed by overriding
 * [createClassGraph] and returning an appropriately configured [ClassGraph] object.
 *
 * There are two ways an enclave is scanned for depending on whether they are mock or non-mock.
 *
 * For mock enclaves, the enclave class is scanned for, similar to how [Class.forName] works. All non-abstract classes
 * extending [com.r3.conclave.enclave.Enclave] are picked up.
 *
 * For non-mock enclaves, "enclave bundles" are scanned for. These are files with a specific path pattern which
 * indicate what type of enclave they are, namely either GraalVM or Gramine, and what mode they are in. GraalVM
 * enclaves are indicated by ending in [GRAALVM_BUNDLE_NAME], whilst Gramine enclaves end in [GRAMINE_BUNDLE_NAME].
 * These bundles are processed by [NativeEnclaveHandle] and [GramineEnclaveHandle], respectively. All enclave
 * bundles reside under a specific root path, [ENCLAVE_BUNDLES_PATH], to reduce the scan space, with the enclave
 * class name forming the next element in the path.
 *
 * The result of the scan is represented by [ScanResult].
 */
open class EnclaveScanner {
    /**
     * Search for an enclave on the classpath.
     * @throws IllegalArgumentException If exactly one enclave isn't found.
     */
    fun findEnclave(): ScanResult = getSingleResult(findEnclaves(), null)

    /**
     * Search for all enclaves on the classpath.
     */
    @Suppress("MemberVisibilityCanBePrivate") // Used by integration tests
    fun findEnclaves(): List<ScanResult> {
        val results = ArrayList<ScanResult>()
        createClassGraph()
            .acceptPaths(ENCLAVE_BUNDLES_PATH)
            .findNonMockEnclaves(results)
        createClassGraph()
            .enableClassInfo()
            .findMockEnclaves(results)
        return results
    }

    /**
     * Search for the given enclave. It is expected the scan will be optimised, rather than scanning the entire
     * classpath.
     * @throws IllegalArgumentException If exactly one matching enclave isn't found.
     */
    fun findEnclave(enclaveClassName: String): ScanResult {
        return getSingleResult(findEnclaves(enclaveClassName), enclaveClassName)
    }

    /**
     * Search for the given enclave across all the mode types.
     */
    @Suppress("MemberVisibilityCanBePrivate") // Used by integration tests
    fun findEnclaves(enclaveClassName: String): List<ScanResult> {
        val results = ArrayList<ScanResult>()
        createClassGraph()
            .acceptPathsNonRecursive("$ENCLAVE_BUNDLES_PATH/$enclaveClassName")
            .findNonMockEnclaves(results)
        createClassGraph()
            .acceptClasses(enclaveClassName)
            .findMockEnclaves(results)
        return results
    }

    private fun ClassGraph.findNonMockEnclaves(results: MutableList<ScanResult>) {
        val pathPattern = Pattern.compile(
            """^$ENCLAVE_BUNDLES_PATH/([^/]+)/(simulation|debug|release)-($GRAALVM_BUNDLE_NAME|$GRAMINE_BUNDLE_NAME)$"""
        )

        scan().use {
            for (resource in it.allResources) {
                val matcher = pathPattern.matcher(resource.path)
                if (!matcher.matches()) continue
                val enclaveClassName = matcher.group(1)
                val enclaveMode = EnclaveMode.valueOf(matcher.group(2).uppercase())
                results += if (matcher.group(3) == GRAALVM_BUNDLE_NAME) {
                    ScanResult.GraalVM(enclaveClassName, enclaveMode, resource.url)
                } else {
                    ScanResult.Gramine(enclaveClassName, enclaveMode, resource.url)
                }
            }
        }
    }

    private fun ClassGraph.findMockEnclaves(results: MutableList<ScanResult>) {
        scan().use {
            for (classInfo in it.getSubclasses("com.r3.conclave.enclave.Enclave")) {
                if (!classInfo.isAbstract) {
                    results += ScanResult.Mock(classInfo.name)
                }
            }
        }
    }

    private fun getSingleResult(results: List<ScanResult>, className: String?): ScanResult {
        when (results.size) {
            1 -> return results[0]
            0 -> {
                val beginning = if (className != null) "Enclave $className does not exist" else "No enclaves found"
                throw IllegalArgumentException(
                    """$beginning on the classpath. Please make sure the gradle dependency to the enclave project is correctly specified:
                        |    runtimeOnly project(path: ":enclave project", configuration: mode)
                        |
                        |    where:
                        |      mode is either "release", "debug", "simulation" or "mock"
                        """.trimMargin()
                )
            }
            else -> throw IllegalStateException("Multiple enclaves were found: $results")
        }
    }

    protected open fun createClassGraph(): ClassGraph = ClassGraph()

    sealed class ScanResult {
        abstract val enclaveClassName: String
        abstract val enclaveMode: EnclaveMode

        data class Mock(override val enclaveClassName: String) : ScanResult() {
            override val enclaveMode: EnclaveMode get() = EnclaveMode.MOCK
            override fun toString(): String = "mock $enclaveClassName"
        }

        class GraalVM(
            override val enclaveClassName: String,
            override val enclaveMode: EnclaveMode,
            val soFileUrl: URL
        ) : ScanResult() {
            init {
                require(enclaveMode != EnclaveMode.MOCK)
            }
            override fun toString(): String = "graalvm ${enclaveMode.name.lowercase()} $enclaveClassName"
        }

        class Gramine(
            override val enclaveClassName: String,
            override val enclaveMode: EnclaveMode,
            val zipFileUrl: URL
        ) : ScanResult() {
            init {
                require(enclaveMode != EnclaveMode.MOCK)
            }
            override fun toString(): String = "gramine ${enclaveMode.name.lowercase()} $enclaveClassName"
        }
    }
}

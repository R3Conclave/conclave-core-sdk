package com.r3.conclave.integrationtests.djvm.base

import com.r3.conclave.integrationtests.djvm.base.DJVMBase.Companion.TEST_WHITELIST
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.execution.ExecutionProfile
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarInputStream
import kotlin.system.exitProcess

fun createSandboxConfiguration(analysisConfiguration: AnalysisConfiguration): SandboxConfiguration {
    return SandboxConfiguration.of(
        ExecutionProfile.UNLIMITED,
        SandboxConfiguration.ALL_RULES,
        SandboxConfiguration.ALL_EMITTERS,
        SandboxConfiguration.ALL_DEFINITION_PROVIDERS,
        analysisConfiguration
    )
}

private fun setup(bootstrapJars: Array<URL>, userSourcesJars: List<Path>) {
    val userSource = object : UserSource, URLClassLoader(userSourcesJars.map { it.toUri().toURL() }.toTypedArray()) {}
    val djvmMemoryClassLoader = object : ApiSource, URLClassLoader(bootstrapJars) {}
    val rootConfiguration = AnalysisConfiguration.createRoot(
        userSource = UserPathSource(emptyList()),
        whitelist = TEST_WHITELIST,
        bootstrapSource = djvmMemoryClassLoader
    )
    val sandboxConfiguration = createSandboxConfiguration(rootConfiguration)
    DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), sandboxConfiguration)
}

fun loadTestClasses(testCodeJars: List<Path>): List<Class<out EnclaveJvmTest>> {
    val classLoader = URLClassLoader(testCodeJars.map { it.toUri().toURL() }.toTypedArray())
    val classes = mutableListOf<Class<out EnclaveJvmTest>>()
    for (jar in testCodeJars) {
        for (clazz in getAllTestClasses(jar)) {
            classes.add(classLoader.loadClass(clazz).asSubclass(EnclaveJvmTest::class.java))
        }
    }
    return classes
}

private fun getAllTestClasses(jarFile: Path): List<String> {
    JarInputStream(Files.newInputStream(jarFile)).use { jar ->
        val classNames = mutableListOf<String>()
        var jarEntry = jar.nextJarEntry
        while (jarEntry != null) {
            /**
             * This suffix convention works around issues regarding auto discovery of tests in a jar.
             * Traversing all the classes and loading them to verify whether they are subclasses of EnclaveTest
             * can lead to issues where transitive dependencies are missing, which can be acceptable if they are
             * not needed to run the test.
             */
            if (!jarEntry.isDirectory && jarEntry.name.endsWith("EnclaveTest.class")) {
                val className = jarEntry.name.replace("/", ".")
                classNames.add(className.removeSuffix(".class"))
            }
            jarEntry = jar.nextJarEntry
        }
        return classNames
    }
}

fun destroy() {
    DJVMBase.destroyRootContext()
}

fun runTest(clazz: Class<out EnclaveJvmTest>) {
    println("Running ${clazz.name}")
    val test = clazz.newInstance()
    val input = (test as? TestSerializable)?.deserializeTestInput(test.getTestInput())
    val result = test.apply(input)
    val serializedTestOutput = test.serializeTestOutput(result)
    test.assertResult(serializedTestOutput)
}

fun runSandboxedTest(clazz: Class<out EnclaveJvmTest>) {
    println("Running in sandbox ${clazz.name}")
    val test = clazz.newInstance()
    val input = (test as? TestSerializable)?.deserializeTestInput(test.getTestInput())
    val result = SandboxRunner().run(clazz.name, input)
    val serializedTestOutput = test.serializeTestOutput(result)
    test.assertResult(serializedTestOutput)
}

/**
 * Run tests on the host JVM, without invoking the enclave infrastructure.
 * Useful to verify the tests are behaving as expected before invoking them in the enclave.
 * This class can be used as an alternative to the JUnit runner, which does not work in Avian.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("USAGE: <deterministic-rt-jar> <tests-jar> ...")
        exitProcess(1)
    }
    val bootstrapJar = Paths.get(args[0]).toUri().toURL()
    val testsFatJars = args.drop(1).map { Paths.get(it) }
    setup(arrayOf(bootstrapJar), testsFatJars)
    val testClasses = loadTestClasses(testsFatJars)
    for (clazz in testClasses) {
        runTest(clazz)
        runSandboxedTest(clazz)
    }
    destroy()
}

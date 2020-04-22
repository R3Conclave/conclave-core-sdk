@file:JvmName("Main")
package com.r3.conclave.jvmtester.testenclave

import com.r3.conclave.jvmtester.api.EnclaveJvmTest
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase.Companion.TEST_WHITELIST
import com.r3.conclave.jvmtester.api.TestAsserter
import com.r3.conclave.jvmtester.testenclave.djvm.SandboxRunner
import com.r3.conclave.jvmtester.testenclave.handlers.SandboxTestHandler
import com.r3.conclave.jvmtester.api.TestSerializable
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.jar.JarInputStream

private lateinit var testClassLoader: ClassLoader

fun setup(bootstrapJars: Array<URL>, userSourcesJars: Array<URL>) {
    val userSource = object : UserSource, URLClassLoader(userSourcesJars) {}
    val djvmMemoryClassLoader = object : ApiSource, URLClassLoader(bootstrapJars) {}
    val rootConfiguration = AnalysisConfiguration.createRoot(
            userSource = UserPathSource(emptyList()),
            whitelist = TEST_WHITELIST,
            bootstrapSource = djvmMemoryClassLoader
    )
    val sandboxConfiguration = SandboxTestHandler.createSandboxConfiguration(rootConfiguration)
    DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), sandboxConfiguration)
}

fun loadTestClasses(classLoader: ClassLoader, testCodeJars: Array<URL>): List<Class<in EnclaveJvmTest>> {
    testClassLoader = classLoader
    val classes = mutableListOf<Class<in EnclaveJvmTest>>()
    for (jar in testCodeJars) {
        for (clazz in getAllTestClasses(jar)) {
            @Suppress("UNCHECKED_CAST")
            classes.add(testClassLoader.loadClass(clazz) as Class<in EnclaveJvmTest>)
        }
    }
    return classes
}

private fun getAllTestClasses(jarURL: URL) : List<String> {
    JarInputStream(jarURL.openStream()).use { jar ->
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

fun runTest(clazz: Class<in EnclaveJvmTest>) {
    println("Running ${clazz.name}")
    val test = clazz.newInstance() as EnclaveJvmTest
    val input = test.getTestInput()
    val result = test.apply(input?.let { (test as TestSerializable).deserializeTestInput(it) })
    val serializedTestOutput = test.serializeTestOutput(result)
    assertResult(clazz.name, serializedTestOutput)
}

fun runSandboxedTest(clazz: Class<in EnclaveJvmTest>) {
    println("Running in sandbox ${clazz.name}")
    val test = clazz.newInstance() as EnclaveJvmTest
    val input = test.getTestInput()
    val result = SandboxRunner().run(clazz.name, input?.let { (test as TestSerializable).deserializeTestInput(it) })
    val serializedTestOutput = test.serializeTestOutput(result)
    assertResult(clazz.name, serializedTestOutput)
}

/**
 * Loads the tests' corresponding asserter class and invokes the assertion function, passing the serialized result
 * as a parameter.
 */
fun assertResult(testClassName: String, testResult: ByteArray) {
    @Suppress("UNCHECKED_CAST")
    val asserterClass = testClassLoader.loadClass(testClassName) as Class<in TestAsserter>
    val testAsserter = asserterClass.newInstance() as TestAsserter
    testAsserter.assertResult(testResult)
}

/**
 * Run tests on the host JVM, without invoking the enclave infrastructure.
 * Useful to verify the tests are behaving as expected before invoking them in the enclave.
 * This class can be used as an alternative to the JUnit runner, which does not work in Avian.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("USAGE: <deterministic-rt-jar> <tests-jar> ...")
        System.exit(1)
    }
    val bootstrapJar = Paths.get(args[0]).toUri().toURL()
    val testsFatJars = args.drop(1).map { Paths.get(it).toUri().toURL() }.toTypedArray()
    setup(arrayOf(bootstrapJar), testsFatJars)
    val testClasses = loadTestClasses(URLClassLoader(testsFatJars), testsFatJars)
    for (clazz in testClasses) {
        runTest(clazz)
        runSandboxedTest(clazz)
    }
    destroy()
}
package com.r3.conclave.init.cli

import com.r3.conclave.init.ConclaveInit
import com.r3.conclave.init.Language
import com.r3.conclave.init.common.printBlock
import com.r3.conclave.init.template.JavaClass
import com.r3.conclave.init.template.JavaPackage
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(CommandLine(ConclaveInitCli()).execute(*args))
}

@CommandLine.Command(
    name = "conclave-init",
    sortOptions = false,
    mixinStandardHelpOptions = true,
    header = ["Conclave Init is a tool for bootstrapping Conclave projects.%n" +
            "See https://github.com/R3Conclave/conclave-core-sdk/wiki/Conclave-Init for more information."],
    headerHeading = "%n",
    synopsisHeading = "%nUsage:%n",
    optionListHeading = "%n",
    // versionProvider is required by picocli when using dynamically loaded version
    versionProvider = ConclaveInitCli.ManifestVersionProvider::class,
)
class ConclaveInitCli : Callable<Int> {
    @CommandLine.Option(
        names = ["-p", "--package"],
        required = true,
        description = ["The base package for your project.\n" +
                "Example: 'com.megacorp'"],
        converter = [JavaPackageConverter::class]
    )
    lateinit var basePackage: JavaPackage

    @CommandLine.Option(
        names = ["-e", "--enclave-class-name"],
        required = true,
        description = ["The java class name for your enclave.\n" +
                "Example: 'AmazingEnclave'"],
        converter = [JavaClassConverter::class]
    )
    lateinit var enclaveClass: JavaClass

    @CommandLine.Option(
        names = ["-t", "--target"],
        required = true,
        description = ["The absolute or relative path to the directory in which to create your new project. " +
                "The directory will be created if it doesn't exist.\n" +
                "Example: ../projects/amazing-conclave-app"],
        converter = [PathConverter::class]
    )
    lateinit var target: Path

    @CommandLine.Option(
        names = ["-l", "--language"],
        description = ["The desired language for your new project. Allowed values: \${COMPLETION-CANDIDATES}.\n" +
                "Default: \${DEFAULT-VALUE}"],
    )
    val language: Language = Language.JAVA

    internal class ManifestVersionProvider : CommandLine.IVersionProvider {
        override fun getVersion(): Array<String> = arrayOf("Conclave Init $CONCLAVE_SDK_VERSION")
    }

    override fun call(): Int {
        checkTargetDoesNotExist()
        projectSummary().printBlock()
        ConclaveInit.createProject(CONCLAVE_SDK_VERSION, language, basePackage, enclaveClass, target)
        return 0
    }

    private fun checkTargetDoesNotExist() {
        if (target.exists()) {
            """
            ERROR: Target directory ${target.absolutePathString()} already exists. Please
            delete the existing directory or specify a different target.
            """.printBlock()

            exitProcess(1)
        }
    }

    private fun projectSummary(): String = """
        Creating new Conclave project.
            Language: $language
            Package: ${basePackage.name}
            Enclave class: ${enclaveClass.name}
            Output directory: ${target.absolutePathString()}
        """.trimIndent()

    companion object {
        private val CONCLAVE_SDK_VERSION = run {
            ConclaveInitCli::class.java.classLoader
                .getResources(MANIFEST_NAME)
                .asSequence()
                .mapNotNull { it.openStream().use(::Manifest).mainAttributes.getValue("Conclave-Release-Version") }
                .firstOrNull()
                ?: throw IllegalStateException("Could not find Conclave-Release-Version in plugin's manifest")
        }
    }
}

internal class JavaPackageConverter : CommandLine.ITypeConverter<JavaPackage> {
    override fun convert(value: String): JavaPackage = JavaPackage(value)
}

internal class JavaClassConverter : CommandLine.ITypeConverter<JavaClass> {
    override fun convert(value: String): JavaClass = JavaClass(value)
}

internal class PathConverter : CommandLine.ITypeConverter<Path> {
    override fun convert(value: String): Path = Paths.get(value.replaceTildeWithHome()).normalize()

    private fun String.replaceTildeWithHome(): String {
        return if (startsWith("~")) {
            replaceFirst("~", System.getProperty("user.home"))
        } else this
    }
}

package com.r3.conclave.init.cli

import com.r3.conclave.init.ConclaveInit
import com.r3.conclave.init.Language
import com.r3.conclave.init.common.printBlock
import com.r3.conclave.init.common.walkTopDown
import com.r3.conclave.init.gradle.configureGradleProperties
import com.r3.conclave.init.template.JavaClass
import com.r3.conclave.init.template.JavaPackage
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.jar.JarInputStream
import kotlin.io.path.*
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(CommandLine(ConclaveInitCli()).execute(*args))
}

@CommandLine.Command(
    name = "conclave-init",
    sortOptions = false,
    mixinStandardHelpOptions = true,
    header = ["Conclave Init is a tool for bootstrapping Conclave projects.%n" +
            "See https://docs.conclave.net/conclave-init.html for more information."],
    headerHeading = "%n",
    synopsisHeading = "%nUsage:%n",
    optionListHeading = "%n",
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

    @CommandLine.Option(
        names = ["-g", "--configure-gradle"],
        description = ["Attempt to configure the user-wide gradle properties. " +
                "See https://docs.conclave.net/gradle-properties.html for more information.\n" +
                "Default: \${DEFAULT-VALUE}"]
    )
    var configureGradle: Boolean = true

    override fun call(): Int {
        checkTargetDoesNotExist()
        if (configureGradle) configureGradle()
        projectSummary().printBlock()
        ConclaveInit.createProject(language, basePackage, enclaveClass, target)
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

    private fun configureGradle() {
        val conclaveRepo = getPathToSdkRepo()
        if (sdkRepoIsValid(conclaveRepo)) {
            val conclaveVersion = getConclaveVersion(conclaveRepo)
            configureGradleProperties(conclaveRepo, conclaveVersion)
        }
    }

    private fun getPathToSdkRepo(): Path {
        val conclaveInitURI = this::class.java.protectionDomain.codeSource.location.toURI()
        val toolsDir = Paths.get(conclaveInitURI).parent
        val sdkDir = toolsDir.parent
        return sdkDir.resolve("repo").normalize()
    }

    private fun sdkRepoIsValid(sdkRepo: Path): Boolean {
        val helpPrefix = """
        WARNING: Could not detect Conclave SDK Repo. Was `conclave-init.jar` moved out
        of the tools directory? Expected directory $sdkRepo
        """.trimIndent()

        try {
            check(sdkRepo.exists()) { "$helpPrefix does not exist." }
            check(sdkRepo.isDirectory()) { "$helpPrefix is not a directory." }
            check(sdkRepo.resolve("com").isDirectory()) { "$helpPrefix is not a Maven repository." }
        } catch (e: IllegalStateException) {
            println(e.message)
            """
            Gradle must be configured manually or the generated project may not compile.

            See https://docs.conclave.net/gradle-properties.html for more information.
            """.printBlock()
            return false
        }
        return true
    }

    private fun getConclaveVersion(sdkRepo: Path): String? {
        val jarsFromRepo = sdkRepo.walkTopDown().filter { it.extension == "jar" }
        val conclaveVersion = jarsFromRepo.firstNotNullOfOrNull { jar ->
            val manifest = jar.inputStream().use { JarInputStream(it).manifest }
            manifest.mainAttributes.getValue("Conclave-Release-Version")
        }

        if (conclaveVersion == null) {
            """
            WARNING: could not detect Conclave version of repo $sdkRepo. Please edit the value
            manually in gradle.properties.
            
            See https://docs.conclave.net/gradle-properties.html for more information.
            """.printBlock()

            return null
        }

        return conclaveVersion
    }

    private fun projectSummary(): String = """
        Creating new Conclave project.
            Language: $language
            Package: ${basePackage.name}
            Enclave class: ${enclaveClass.name}
            Output directory: ${target.absolutePathString()}
        """.trimIndent()
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
package com.r3.conclave.init.cli

import com.r3.conclave.init.ConclaveInit
import com.r3.conclave.init.Language
import com.r3.conclave.init.common.walkTopDown
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

    private val pathToSdkRepo: Path by lazy {
        val conclaveInitURI = this::class.java.protectionDomain.codeSource.location.toURI()
        val toolsDir = Paths.get(conclaveInitURI).parent
        val sdkDir = toolsDir.parent
        return@lazy sdkDir.resolve("repo").normalize()
    }

    @CommandLine.Option(
        names = ["-s", "--sdk-repo"],
        description = ["The path to the Conclave SDK repo, which will be copied into the target project.\n" +
                "Default: /path/to/conclave-sdk/repo"],
        converter = [PathConverter::class],
    )
    val sdkRepo: Path = pathToSdkRepo


    @CommandLine.Option(
        names = ["-l", "--language"],
        description = ["The desired language for your new project. Allowed values: \${COMPLETION-CANDIDATES}.\n" +
                "Default: \${DEFAULT-VALUE}"],
    )
    val language: Language = Language.JAVA

    override fun call(): Int {
        validateSdkRepo()
        checkTargetDoesNotExist()
        val conclaveVersion = getConclaveVersion(sdkRepo)
        println(projectSummary())
        ConclaveInit.createProject(language, basePackage, enclaveClass, target, sdkRepo, conclaveVersion)
        return 0
    }

    private fun validateSdkRepo() {
        val sdkRepoErrorHelp = "Expected to find Conclave SDK Repo at " +
                "$sdkRepo. Specify another directory via the --sdk-repo parameter. " +
                "It should be the path to the `repo/` subdirectory of the Conclave SDK."

        try {
            check(sdkRepo.exists()) { "$sdkRepo does not exist. $sdkRepoErrorHelp" }
            check(sdkRepo.isDirectory()) { "$sdkRepo is not a directory. $sdkRepoErrorHelp" }
            check(sdkRepo.resolve("com").isDirectory()) { "$sdkRepo is not a Maven repository. $sdkRepoErrorHelp" }
        } catch (e: IllegalStateException) {
            println(e.message)
            exitProcess(1)
        }
    }

    private fun checkTargetDoesNotExist() {
        if (target.exists()) {
            println( "Target directory ${target.absolutePathString()} already exists. " +
                        "Please delete the existing directory or specify a different target." )
            exitProcess(1)
        }
    }

    private fun getConclaveVersion(sdkRepo: Path): String {
        val jarsFromRepo = sdkRepo.walkTopDown().filter { it.extension == "jar" }
        val conclaveVersion = jarsFromRepo.firstNotNullOfOrNull { jar ->
            val manifest = jar.inputStream().use { JarInputStream(it).manifest }
            manifest.mainAttributes.getValue("Conclave-Release-Version")
        }

        if (conclaveVersion == null) {
            System.err.println(
                "Error: could not detect Conclave version of repo $sdkRepo. Please add it manually " +
                        "to the gradle.properties file in the root of the generated project."
            )

            return ""
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
    override fun convert(value: String): Path = Paths.get(value).normalize()
}
package com.r3.conclave.init.cli

import com.r3.conclave.init.Language
import com.r3.conclave.init.template.JavaClass
import com.r3.conclave.init.template.JavaPackage
import picocli.CommandLine
import picocli.CommandLine.ITypeConverter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


class CommandLineParameters {
    @CommandLine.Option(
        names = ["-h", "--help"],
        usageHelp = true,
        description = ["Display this help message."]
    )
    var helpInfoRequested: Boolean = false

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

    fun validateSdkRepo() {
        val sdkRepoErrorHelp = "Expected to find Conclave SDK Repo at " +
                "$sdkRepo. Specify another directory via the --sdk-repo parameter. " +
                "It should be the path to the `repo/` subdirectory of the Conclave SDK."

        require(sdkRepo.exists()) { "$sdkRepo does not exist. $sdkRepoErrorHelp" }
        require(sdkRepo.isDirectory()) { "$sdkRepo is not a directory. $sdkRepoErrorHelp" }
        require(sdkRepo.resolve("com").isDirectory()) { "$sdkRepo is not a Maven repository. $sdkRepoErrorHelp" }
    }


    @CommandLine.Option(
        names = ["-l", "--language"],
        description = ["The desired language for your new project. Allowed values: \${COMPLETION-CANDIDATES}.\n" +
                "Default: \${DEFAULT-VALUE}"],
    )
    val language: Language = Language.JAVA


    fun projectSummary(): String = """
        Creating new Conclave project.
            Language: $language
            Package: ${basePackage.name}
            Enclave class: ${enclaveClass.name}
            Output directory: ${target.absolutePathString()}
        """.trimIndent()
}

internal class JavaPackageConverter : ITypeConverter<JavaPackage> {
    override fun convert(value: String): JavaPackage = JavaPackage(value)
}

internal class JavaClassConverter : ITypeConverter<JavaClass> {
    override fun convert(value: String): JavaClass = JavaClass(value)
}

internal class PathConverter: ITypeConverter<Path> {
    override fun convert(value: String): Path = Paths.get(value).normalize()
}
package com.r3.conclave.init.cli

import com.r3.conclave.init.Language
import com.r3.conclave.init.template.JavaClass
import com.r3.conclave.init.template.JavaPackage
import picocli.CommandLine
import picocli.CommandLine.ITypeConverter
import java.nio.file.Path
import kotlin.io.path.absolutePathString


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
                "Example: ../projects/amazing-conclave-app"]
    )
    lateinit var target: Path

    @CommandLine.Option(
        names = ["-l", "--language"],
        description = ["The desired language for your new project. Allowed values: \${COMPLETION-CANDIDATES}.\n" +
                "Default: \${DEFAULT-VALUE}"],
    )
    val language: Language = Language.java


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
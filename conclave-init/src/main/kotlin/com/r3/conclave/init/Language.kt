package com.r3.conclave.init

import com.r3.conclave.init.template.JavaClass
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension

enum class Language(private val extension: String, val enclaveClass: JavaClass) {
    KOTLIN("kt", JavaClass("TemplateEnclaveKotlin")),
    JAVA("java", JavaClass("TemplateEnclaveJava"));

    fun matches(path: Path): Boolean {
        // Ignore files that aren't inside a src directory
        if (!path.contains(Path("src"))) return true

        return path.extension == extension
    }

    override fun toString(): String = name.lowercase()
}
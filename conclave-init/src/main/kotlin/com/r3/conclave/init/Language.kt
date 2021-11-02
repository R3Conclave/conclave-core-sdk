package com.r3.conclave.init

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension

enum class Language(private val extension: String) {
    KOTLIN("kt"),
    JAVA("java");

    fun matches(path: Path): Boolean {
        // Ignore files that aren't inside a src directory
        if (!path.contains(Path("src"))) return true

        return path.extension == extension
    }

    override fun toString(): String = name.lowercase()
}
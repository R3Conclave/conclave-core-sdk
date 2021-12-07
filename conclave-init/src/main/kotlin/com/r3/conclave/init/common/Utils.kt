package com.r3.conclave.init.common

import java.io.File
import java.nio.file.Path

fun String.printBlock() {
    println()
    println(this.trimIndent())
    println()
}

// Path Extensions
fun Path.walkTopDown(): Sequence<Path> = toFile().walkTopDown().map(File::toPath)
fun Path.deleteRecursively(): Boolean = toFile().deleteRecursively()
fun Path?.equals(other: Path): Boolean =
    this != null && this.normalize().toAbsolutePath() == other.normalize().toAbsolutePath()


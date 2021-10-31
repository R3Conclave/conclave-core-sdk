package com.r3.conclave.init.common

import java.io.File
import java.nio.file.Path

// Path Extensions
fun Path.walkTopDown(): Sequence<Path> = toFile().walkTopDown().map(File::toPath)
fun Path.copyRecursively(target: Path): Boolean = toFile().copyRecursively(target.toFile())
fun Path.deleteRecursively(): Boolean = toFile().deleteRecursively()


package com.r3.conclave.init.template

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.relativeTo

interface TransformPath : (Path) -> Path

class TransformPackage(private val javaPackage: JavaPackage) : TransformPath {
    override fun invoke(path: Path): Path =
        path.toString().replace(templateEnclavePackage.dirs, javaPackage.dirs).let(::Path)
}

class TransformClassName(private val templateEnclaveClass: JavaClass, private val projectEnclaveClass: JavaClass) :
    TransformPath {
    override fun invoke(path: Path): Path =
        path.toString().replace(templateEnclaveClass.name, projectEnclaveClass.name).let(::Path)
}

class TransformBasePath(private val templateRoot: Path, private val projectRoot: Path) : TransformPath {
    override fun invoke(path: Path): Path {
        val relative = path.relativeTo(templateRoot)
        return projectRoot.resolve(relative)
    }
}

class TemplatePathTransformer(
    private val basePackage: JavaPackage,
    private val templateRoot: Path,
    private val projectRoot: Path,
    private val templateEnclaveClass: JavaClass,
    private val projectEnclaveClass: JavaClass,
) {
    fun transform(paths: Sequence<Path>): Sequence<Path> = paths
        .map(TransformBasePath(templateRoot, projectRoot))
        .map(TransformPackage(basePackage))
        .map(TransformClassName(templateEnclaveClass, projectEnclaveClass))
}

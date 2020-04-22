package com.r3.conclave.jvmtester.djvm.testsauxiliary

import java.io.IOException
import java.nio.file.Path
import java.util.jar.JarOutputStream

@FunctionalInterface
interface JarWriter {

    @Throws(IOException::class)
    fun write(jar: JarOutputStream, path: Path)

}
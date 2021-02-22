package com.r3.conclave.enclave.internal.substratevm

import org.graalvm.nativeimage.c.CContext.Directives

class PosixDirectives : Directives {
    override fun getHeaderFiles(): List<String> {
        /*
         * The header files with the C declarations that are imported.
         * The files are resolved according to the include directories set in NativeImage.
         */
        return listOf(
                "\"conclave-stat.h\"",
                "\"conclave-timespec.h\""
        )
    }
}

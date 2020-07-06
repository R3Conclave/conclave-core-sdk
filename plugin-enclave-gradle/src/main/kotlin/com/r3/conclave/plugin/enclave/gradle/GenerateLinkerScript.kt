package com.r3.conclave.plugin.enclave.gradle

import java.nio.file.Files
import java.nio.file.Path

open class GenerateLinkerScript {

    companion object {
        // graal_create_isolate is included to allow the SVM GDB python helper to load when
        // debugging graal code inside an enclave
        val content = """
            enclave.so
            {
                global:
                    g_global_data_sim;
                    g_global_data;
                    enclave_entry;
                    g_peak_heap_used;
                    g_peak_rsrv_mem_committed;
                    graal_create_isolate;
                local:
                    *;
            };
        """.trimIndent()

        @JvmStatic
        fun writeToFile(linkerScript: Path) {
            if (!Files.exists(linkerScript.parent)) {
                Files.createDirectories(linkerScript.parent)
            }
            Files.write(linkerScript, content.toByteArray())
        }
    }
}
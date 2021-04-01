package com.r3.conclave.plugin.enclave.gradle

import java.nio.file.Files
import java.nio.file.Path

class GenerateDefaultResourcesConfig {

    companion object {
        private val content = """
        {
          "resources": {
            "includes": [
              {"pattern": ".*/intel-.*pem"}
            ]
          }
        }
        """.trimIndent()

        fun writeToFile(resourcesConfigPath: Path) {
            Files.write(resourcesConfigPath.toAbsolutePath(), content.toByteArray())
        }
    }
}

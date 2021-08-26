package com.r3.conclave.plugin.enclave.gradle

import java.nio.file.Path

operator fun Path.div(other: String): Path = resolve(other)
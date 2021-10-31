package com.r3.conclave.init.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class ManifestTest {
    @Test
    fun `get conclave version from manifest file`() {
        val files = sequenceOf(
        """
        Manifest-Version: 1.0
        Main-Class: org.GNOME.Accessibility.AtkWrapper
        Created-By: 11.0.5 (Ubuntu)
        """,
        """
        Manifest-Version: 1.0
        Conclave-Release-Version: 1.2-SNAPSHOT
        Conclave-Revision: c9aaf81e3ffe8090ca1cfac12738e4c16cc31c4d
        Main-Class: com.r3.conclave.init.cli.MainKt
        """
        ).map(String::trimIndent)


        assertEquals("1.2-SNAPSHOT", ManifestFiles(files).conclaveVersion)
    }

    @Test
    fun `throws if version not present`() {
        val files = sequenceOf(
        """
        Manifest-Version: 1.0
        Main-Class: org.GNOME.Accessibility.AtkWrapper
        Created-By: 11.0.5 (Ubuntu)
        """
        ).map(String::trimIndent)


        assertThrows<IOException> { ManifestFiles(files).conclaveVersion }
    }
}
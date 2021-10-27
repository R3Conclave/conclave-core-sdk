package com.r3.conclave.init.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaPackageTest {
    private val packageAndClass = JavaPackage("com.megacorp")

    @Test
    fun `get package dirs`() {
        assertEquals("com/megacorp", packageAndClass.dirs)
    }

    @Test
    fun `get package name`() {
        assertEquals("com.megacorp", packageAndClass.name)
    }
}
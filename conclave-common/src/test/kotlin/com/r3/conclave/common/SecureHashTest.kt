package com.r3.conclave.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SecureHashTest {
    @Test
    fun `parse SHA-256`() {
        assertThat(SHA256Hash.parse("214557c20de2a4b65661a83bed4dfff161b63397793bda3eacf50539eb77fc83"))
            .isEqualTo(SecureHash.parse("214557c20de2a4b65661a83bed4dfff161b63397793bda3eacf50539eb77fc83"))
    }

    @Test
    fun `parse SHA-512`() {
        assertThat(SHA512Hash.parse("59e7c07cede1899597570e89f390960790b77ac030898be8a0111f483f3e914f6eae4b8427ff61049fada5b9bbeb206ca3ffb9352d6a6dcef5252ec7bee5acad"))
            .isEqualTo(SecureHash.parse("59e7c07cede1899597570e89f390960790b77ac030898be8a0111f483f3e914f6eae4b8427ff61049fada5b9bbeb206ca3ffb9352d6a6dcef5252ec7bee5acad"))
    }
}

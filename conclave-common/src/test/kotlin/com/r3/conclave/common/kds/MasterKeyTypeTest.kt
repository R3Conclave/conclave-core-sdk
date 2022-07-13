package com.r3.conclave.common.kds

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.lang.IllegalArgumentException

class MasterKeyTypeTest {
    @Test
    fun `master key type IDs are unique`() {
        val masterKeyIDs = MasterKeyType.values().map { it.id }
        val uniqueIDs = masterKeyIDs.toSet()
        assertThat(uniqueIDs.size).isEqualTo(masterKeyIDs.size)
    }

    @Test
    fun `master key type fromID throws when ID number is invalid`() {
        assertThrows<IllegalArgumentException> {
            MasterKeyType.fromID(9001)
        }
    }

    @ParameterizedTest
    @EnumSource(MasterKeyType::class)
    fun `master key type fromID works for all valid master key types`(masterKeyType: MasterKeyType) {
        assertDoesNotThrow {
            MasterKeyType.fromID(masterKeyType.id)
        }
    }
}

package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CpuFeature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals

class CpuFeatureTest {
    @Test
    fun `EnclaveHost CPU contains GENERIC_IA32`() {
        val features = NativeApi.cpuFeatures
        assertTrue(features.contains(CpuFeature.GENERIC_IA32))
    }

    @Test
    fun `EnclaveHost CPU calls produce same result - 100 iterations`() {
        val featuresBase = NativeApi.cpuFeatures
        repeat(100) {
            val features = NativeApi.cpuFeatures
            assertEquals(featuresBase,features)
        }
    }

    @Test
    fun `Native getCpuFeatures produce same result = 1000 iterations`() {
        val featuresBase = NativeShared.getCpuFeatures()
        repeat(1000) {
            val features = NativeShared.getCpuFeatures()
            assertEquals(featuresBase, features)
        }
    }
}

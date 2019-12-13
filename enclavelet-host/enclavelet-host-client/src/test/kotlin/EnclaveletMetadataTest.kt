package com.r3.sgx.enclavelethost.client

import com.r3.sgx.core.common.attestation.Measurement
import org.junit.Test
import kotlin.test.assertEquals

class EnclaveletMetadataTest {

    @Test
    fun testReadMeasurementFromMetadata() {
        val meta = EnclaveletMetadata.read(javaClass.getResourceAsStream("/enclave.metadata.yml"))
        assertEquals("com.r3.sgx.enclave.bar", meta.className)
        assertEquals(barEnclaveMeasurement, meta.measurement)
    }

    private companion object {
        private val barEnclaveMeasurement = Measurement.of(
                "d0f10f0fd65e9db5d0e3e12e9871c2f40dfd442e864a09d5850a02905cea3476")
    }
}
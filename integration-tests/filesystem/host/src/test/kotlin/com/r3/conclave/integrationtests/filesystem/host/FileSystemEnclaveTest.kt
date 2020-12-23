package com.r3.conclave.integrationtests.filesystem.host

import com.google.protobuf.ByteString
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.util.concurrent.atomic.AtomicInteger

open class FileSystemEnclaveTest {

    companion object {
        val uid = AtomicInteger()
        private lateinit var enclaveHost : EnclaveHost
        private var referenceCounter = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            synchronized(referenceCounter) {
                if (++referenceCounter > 0) {
                    val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                    val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
                    enclaveHost = EnclaveHost.load("com.r3.conclave.integrationtests.filesystem.enclave.FileSystemEnclave")
                    enclaveHost.start(AttestationParameters.EPID(spid, attestationKey), null)
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun destroy() {
            synchronized(referenceCounter) {
                if (--referenceCounter <= 0 && ::enclaveHost.isInitialized) {
                    enclaveHost.close()
                }
            }
        }

        fun request(type: Request.Type, path: String? = null, data: ByteArray? = null, offset: Int? = null,
                    length: Int? = null, uid: Int? = null, append: Boolean? = null) : ByteArray? {
            val request = Request.newBuilder().setType(type)
            if (path != null) {
                request.path = path
            }
            if (data != null) {
                request.data = ByteString.copyFrom(data)
            }
            if (offset != null) {
                request.offset = offset
            }
            if (length != null) {
                request.length = length
            }
            if (uid != null) {
                request.uid = uid
            }
            if (append != null) {
                request.append = append
            }
            return enclaveHost.callEnclave(request.build().toByteArray())
        }
    }
}
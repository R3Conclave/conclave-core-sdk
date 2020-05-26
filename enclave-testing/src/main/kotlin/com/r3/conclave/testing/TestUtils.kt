package com.r3.conclave.testing

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.SHA512Hash
import com.r3.conclave.common.internal.*
import java.security.PublicKey
import java.util.function.Supplier
import kotlin.random.Random

fun createSignedQuote(
        cpuSvn: OpaqueBytes = OpaqueBytes(Random.nextBytes(16)),
        measurement: SHA256Hash = SHA256Hash.wrap(Random.nextBytes(32)),
        mrsigner: SHA256Hash = SHA256Hash.wrap(Random.nextBytes(32)),
        isvProdId: Int = 1,
        isvSvn: Int = 1,
        dataSigningKey: PublicKey = SignatureSchemeEdDSA().generateKeyPair().public
): ByteCursor<SgxSignedQuote> {
    return Cursor.allocate(SgxSignedQuote(500)).apply {
        quote[SgxQuote.reportBody].apply {
            this[SgxReportBody.cpuSvn] = cpuSvn.buffer()
            this[SgxReportBody.attributes][SgxAttributes.flags] = SgxEnclaveFlags.DEBUG
            this[SgxReportBody.measurement] = measurement.buffer()
            this[SgxReportBody.mrsigner] = mrsigner.buffer()
            this[SgxReportBody.isvProdId] = isvProdId
            this[SgxReportBody.isvSvn] = isvSvn
            this[SgxReportBody.reportData] = SHA512Hash.hash(dataSigningKey.encoded).buffer()
        }
    }
}

fun expectWithin(seconds: Int, condition: Supplier<Boolean>): Boolean {
    for (i in 0 until seconds) {
        if (condition.get()) {
            return true
        }
        Thread.sleep(1000)
    }
    return false
}

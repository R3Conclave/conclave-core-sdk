package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.host.internal.GramineNative
import com.r3.conclave.host.internal.NativeLoader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class EnclaveGramineQuoteServiceDCAP : EnclaveQuoteService() {

    companion object {
        private const val SGX_QUOTE_MAX_SIZE = 8192
    }

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        val targetInfo = Cursor.allocate(SgxTargetInfo)
        System.loadLibrary("gramine_dcap")
        GramineNative.initQuoteDCAP("/usr/lib/")
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val reportData = report[body][reportData]
        val quoteBytes = getQuoteFromGramine(reportData.bytes)
        return Cursor.wrap(SgxSignedQuote, quoteBytes)
    }

    private fun getQuoteFromGramine(enclaveTargetInfoBytes: ByteArray): ByteArray {
        setUserData(enclaveTargetInfoBytes)

        return try {
            FileInputStream("/dev/attestation/quote").use {
                it.readBytes().copyOf(SGX_QUOTE_MAX_SIZE)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
    }

    private fun setUserData(data: ByteArray) {
        try {
            FileOutputStream("/dev/attestation/user_report_data").use {
                it.write(data)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
    }
}

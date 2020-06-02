package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SignatureSchemeId
import com.r3.conclave.common.internal.SignatureSchemeFactory

interface EnclaveApi {
    /**
     * Create an SGX report.
     * @param targetInfoIn optional information of the target enclave if the report is to be used as part of local
     *     attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     *   @see SgxTargetInfo
     * @param reportDataIn optional data to be included in the report. If null the data area of the report will be 0.
     *   @see SgxReportData
     * @param reportOut the byte array to put the report in. The size should be the return value of [SgxReport.size].
     *   @see SgxReport
     * sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
     * TODO change this to use ByteBuffers directly
     */
    fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray)

    /**
     * Fill [output] with indices in [[offset], [offset] + [length]) with random bytes using the RDRAND instruction.
     */
    fun getRandomBytes(output: ByteArray, offset: Int, length: Int)

    /**
     * Factory function giving access to cryptographic signature scheme implementations.
     */
    @JvmDefault
    fun getSignatureScheme(spec: SignatureSchemeId): SignatureScheme {
        return SignatureSchemeFactory.make(spec)
    }
    /**
     * Fill [output] with random bytes.
     */
    @JvmDefault
    fun getRandomBytes(output: ByteArray) {
        getRandomBytes(output, 0, output.size)
    }

    /**
     * @return true if the enclave was loaded in debug mode, i.e. its report's DEBUG flag is set, false otherwise.
     */
    @JvmDefault
    fun isDebugMode(): Boolean {
        val isEnclaveDebug = isEnclaveDebug
        return if (isEnclaveDebug == null) {
            val report = Cursor.allocate(SgxReport)
            createReport(null, null, report.getBuffer().array())
            val enclaveFlags = report[SgxReport.body][SgxReportBody.attributes][SgxAttributes.flags].read()
            val result = enclaveFlags and SgxEnclaveFlags.DEBUG != 0L
            EnclaveApi.isEnclaveDebug = result
            result
        } else {
            isEnclaveDebug
        }
    }

    /**
     * @return true if the enclave is a simulation enclave, false otherwise.
     */
    @JvmDefault
    fun isSimulation(): Boolean {
        return Native.isEnclaveSimulation()
    }

    private companion object {
        var isEnclaveDebug: Boolean? = null
    }
}

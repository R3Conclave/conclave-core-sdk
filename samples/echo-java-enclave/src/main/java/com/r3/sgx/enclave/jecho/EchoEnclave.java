package com.r3.sgx.enclave.jecho;

import com.r3.sgx.core.common.Cursor;
import com.r3.sgx.core.common.SgxReportData;
import com.r3.sgx.core.enclave.EnclaveApi;
import com.r3.sgx.core.enclave.Enclavelet;

import java.nio.ByteBuffer;

public class EchoEnclave extends Enclavelet {
    @Override
    public Cursor<ByteBuffer, SgxReportData> createReportData(EnclaveApi api) {
        Cursor<ByteBuffer, SgxReportData> report = Cursor.allocate(SgxReportData.INSTANCE);
        ByteBuffer buffer = report.getBuffer();
        buffer.put(new byte[buffer.capacity()]);
        return report;
    }

    @Override
    public EchoHandler createHandler(EnclaveApi api) {
        return new EchoHandler();
    }
}

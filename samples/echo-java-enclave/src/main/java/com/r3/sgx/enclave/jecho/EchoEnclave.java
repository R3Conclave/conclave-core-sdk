package com.r3.sgx.enclave.jecho;

import com.r3.conclave.common.internal.Cursor;
import com.r3.conclave.common.internal.SgxReportData;
import com.r3.sgx.core.enclave.EnclaveApi;
import com.r3.sgx.core.enclave.Enclavelet;

import java.nio.ByteBuffer;

public class EchoEnclave extends Enclavelet {
    @Override
    public Cursor<SgxReportData, ByteBuffer> createReportData(EnclaveApi api) {
        Cursor<SgxReportData, ByteBuffer> report = Cursor.allocate(SgxReportData.INSTANCE);
        ByteBuffer buffer = report.getBuffer();
        buffer.put(new byte[buffer.capacity()]);
        return report;
    }

    @Override
    public EchoHandler createHandler(EnclaveApi api) {
        return new EchoHandler();
    }
}

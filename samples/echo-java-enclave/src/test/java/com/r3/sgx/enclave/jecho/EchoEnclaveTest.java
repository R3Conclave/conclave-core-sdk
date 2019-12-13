package com.r3.sgx.enclave.jecho;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import com.r3.sgx.core.common.BytesHandler;
import com.r3.sgx.core.common.ChannelInitiatingHandler;
import com.r3.sgx.core.host.EnclaveHandle;
import com.r3.sgx.core.host.EnclaveLoadMode;
import com.r3.sgx.core.host.NativeHostApi;
import com.r3.sgx.testing.BytesRecordingHandler;
import com.r3.sgx.testing.RootHandler;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class EchoEnclaveTest {
    private static final String ENCLAVE_PATH = System.getProperty("com.r3.sgx.enclave.path");

    private BytesRecordingHandler handler;
    private EnclaveHandle<RootHandler.Connection> enclaveHandle;

    @Before
    public void setupEnclave() {
        handler = new BytesRecordingHandler();
        NativeHostApi hostApi = new NativeHostApi(EnclaveLoadMode.SIMULATION);
        enclaveHandle = hostApi.createEnclave(new RootHandler(), new File(ENCLAVE_PATH));
    }

    @Test
    public void testEchoEnclave() throws InterruptedException, ExecutionException {
        RootHandler.Connection connection = enclaveHandle.getConnection();
        ChannelInitiatingHandler.Connection channels = connection.addDownstream(new ChannelInitiatingHandler());
        BytesHandler.Connection channel = channels.addDownstream(handler).get().getConnection();
        channel.send(ByteBuffer.wrap("Hello Java enclave".getBytes(UTF_8)));
        assertEquals(1, handler.getSize());
        ByteBuffer buffer = handler.getNextCall();
        byte[] response = new byte[buffer.remaining()];
        buffer.get(response);
        assertEquals("Hello Java enclave", new String(response, UTF_8));
    }
}

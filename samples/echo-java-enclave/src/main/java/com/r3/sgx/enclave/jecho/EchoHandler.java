package com.r3.sgx.enclave.jecho;

import com.r3.sgx.core.common.Handler;
import com.r3.sgx.core.common.Sender;

import java.nio.ByteBuffer;

class EchoHandler implements Handler<Sender> {
    @Override
    public Sender connect(Sender upstream) {
        return upstream;
    }

    @Override
    public void onReceive(Sender sender, ByteBuffer input) {
        sender.send(input.remaining(), buffer -> buffer.put(input));
    }
}

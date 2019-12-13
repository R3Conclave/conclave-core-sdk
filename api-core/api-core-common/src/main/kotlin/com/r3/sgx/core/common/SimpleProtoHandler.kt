package com.r3.sgx.core.common

import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Parser

abstract class SimpleProtoHandler<IN, OUT : GeneratedMessageV3>(parser: Parser<IN>) : ProtoHandler<IN, OUT, ProtoSender<OUT>>(parser) {
    final override fun connect(upstream: ProtoSender<OUT>) = upstream
}

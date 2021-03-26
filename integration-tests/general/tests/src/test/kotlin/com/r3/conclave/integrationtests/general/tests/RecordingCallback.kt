package com.r3.conclave.integrationtests.general.tests

class RecordingCallback : (ByteArray) -> ByteArray? {
    val calls = ArrayList<ByteArray>()

    override fun invoke(bytes: ByteArray): ByteArray? {
        calls += bytes
        return null
    }
}

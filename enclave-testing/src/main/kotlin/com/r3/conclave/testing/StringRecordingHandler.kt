package com.r3.conclave.testing

/**
 * A utility String-based enclave host that records all calls into a list.
 */
class StringRecordingHandler : StringHandler() {
    var calls = ArrayList<String>()
    override fun onReceive(sender: StringSender, string: String) {
        calls.add(string)
    }
}

package com.r3.conclave.enclave.internal.substratevm.mock

import com.r3.conclave.enclave.internal.substratevm.Timespec
import com.r3.conclave.jvm.enclave.common.internal.testing.MockTimespecData
import org.graalvm.word.ComparableWord

class MockTimeSpec : Timespec {
    private val timespec = MockTimespecData()

    override fun rawValue(): Long {
        TODO("Not yet implemented")
    }

    override fun equal(`val`: ComparableWord?): Boolean {
        TODO("Not yet implemented")
    }

    override fun notEqual(`val`: ComparableWord?): Boolean {
        TODO("Not yet implemented")
    }

    override fun isNull(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isNonNull(): Boolean {
        TODO("Not yet implemented")
    }

    override fun tv_sec(): Long {
        return timespec.sec
    }

    override fun setTvSec(tvSec: Long) {
        timespec.sec = tvSec
    }

    override fun tv_nsec(): Long {
        return timespec.nsec
    }

    override fun setTvNsec(tvNsec: Long) {
        timespec.nsec = tvNsec
    }
}
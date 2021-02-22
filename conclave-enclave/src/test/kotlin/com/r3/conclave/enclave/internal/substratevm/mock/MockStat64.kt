package com.r3.conclave.enclave.internal.substratevm.mock

import com.r3.conclave.enclave.internal.substratevm.Stat
import com.r3.conclave.enclave.internal.substratevm.Timespec
import org.graalvm.word.ComparableWord

class MockStat64 : Stat.Stat64 {
    private val timeSpec: Timespec = MockTimeSpec()

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

    override fun st_mtim(): Timespec {
        return timeSpec
    }
}
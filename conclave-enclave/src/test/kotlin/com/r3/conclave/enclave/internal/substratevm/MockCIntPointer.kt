package com.r3.conclave.enclave.internal.substratevm

import org.graalvm.nativeimage.c.type.CIntPointer
import org.graalvm.word.ComparableWord
import org.graalvm.word.SignedWord

class MockCIntPointer(var value: Int) : CIntPointer {
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

    override fun read(): Int {
        TODO("Not yet implemented")
    }

    override fun read(index: Int): Int {
        TODO("Not yet implemented")
    }

    override fun read(index: SignedWord?): Int {
        TODO("Not yet implemented")
    }

    override fun write(value: Int) {
        this.value = value
    }

    override fun write(index: Int, value: Int) {
        TODO("Not yet implemented")
    }

    override fun write(index: SignedWord?, value: Int) {
        TODO("Not yet implemented")
    }

    override fun addressOf(index: Int): CIntPointer {
        TODO("Not yet implemented")
    }

    override fun addressOf(index: SignedWord?): CIntPointer {
        TODO("Not yet implemented")
    }
}
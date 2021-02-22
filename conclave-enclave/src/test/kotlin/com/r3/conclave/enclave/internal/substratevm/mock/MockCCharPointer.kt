package com.r3.conclave.enclave.internal.substratevm.mock

import org.graalvm.nativeimage.c.type.CCharPointer
import org.graalvm.word.ComparableWord
import org.graalvm.word.SignedWord

class MockCCharPointer : CCharPointer {
    val byteArray: ByteArray

    constructor(size: Int) {
        byteArray = ByteArray(size)
    }

    constructor(s: String) {
        byteArray = s.toByteArray()
    }

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

    override fun read(): Byte {
        TODO("Not yet implemented")
    }

    override fun read(index: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read(index: SignedWord?): Byte {
        TODO("Not yet implemented")
    }

    override fun write(value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write(index: Int, value: Byte) {
        byteArray[index] = value
    }

    override fun write(index: SignedWord?, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun addressOf(index: Int): CCharPointer {
        TODO("Not yet implemented")
    }

    override fun addressOf(index: SignedWord?): CCharPointer {
        TODO("Not yet implemented")
    }

}
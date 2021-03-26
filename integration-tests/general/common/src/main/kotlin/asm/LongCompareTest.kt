package avian.test.asm

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM6
import java.lang.reflect.Method
import java.time.Instant

class LongCompareTest {
    companion object {
        const val API_VERSION = ASM6

        private fun expect(v: Boolean) {
            if (!v) throw RuntimeException()
        }

        // Dummy implementation - this method's bytecode will be rewritten at runtime.
        @Suppress("unused")
        @JvmStatic
        fun compare(a: Long, b: Long): Int {
            if (a > b) {
                return 1
            }
            if (a < b) {
                return -1
            }
            return 0
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val clazz = LocalClassLoader().generateClass(LongCompareTest::class.java.name)
            val method = clazz.getMethod("compare", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            testInstantMinA(method)
            testInstantMinB(method)
            testMinusOneA(method)
            testMinusOneB(method)
            testOneA(method)
            testOneB(method)
            testMinLongA(method)
            testMinLongB(method)
            testMaxLongA(method)
            testMaxLongB(method)
            testMaxMin(method)
            testMinMax(method)
            testEqualityZero(method)
            testEqualityMinLong(method)
            testEqualityMaxLong(method)
        }

        @JvmStatic
        fun testInstantMinA(method: Method) {
            val result = method.invoke(null, Instant.MIN.epochSecond, 0L)
            expect(result == -1)
        }

        @JvmStatic
        fun testInstantMinB(method: Method) {
            val result = method.invoke(null, 0L, Instant.MIN.epochSecond)
            expect(result == 1)
        }

        @JvmStatic
        fun testMinusOneA(method: Method) {
            val result = method.invoke(null, -1L, 0L)
            expect(result == -1)
        }

        @JvmStatic
        fun testMinusOneB(method: Method) {
            val result = method.invoke(null, 0L, -1L)
            expect(result == 1)
        }

        @JvmStatic
        fun testOneA(method: Method) {
            val result = method.invoke(null, 1L, 0L)
            expect(result == 1)
        }

        @JvmStatic
        fun testOneB(method: Method) {
            val result = method.invoke(null, 0L, 1L)
            expect(result == -1)
        }

        @JvmStatic
        fun testMinLongA(method: Method) {
            val result = method.invoke(null, Long.MIN_VALUE, 0L)
            expect(result == -1)
        }

        @JvmStatic
        fun testMinLongB(method: Method) {
            val result = method.invoke(null, 0L, Long.MIN_VALUE)
            expect(result == 1)
        }

        @JvmStatic
        fun testMaxLongA(method: Method) {
            val result = method.invoke(null, Long.MAX_VALUE, 0L)
            expect(result == 1)
        }

        @JvmStatic
        fun testMaxLongB(method: Method) {
            val result = method.invoke(null, 0L, Long.MAX_VALUE)
            expect(result == -1)
        }

        @JvmStatic
        fun testMaxMin(method: Method) {
            val result = method.invoke(null, Long.MAX_VALUE, Long.MIN_VALUE)
            expect(result == 1)
        }

        @JvmStatic
        fun testMinMax(method: Method) {
            val result = method.invoke(null, Long.MIN_VALUE, Long.MAX_VALUE)
            expect(result == -1)
        }

        @JvmStatic
        fun testEqualityZero(method: Method) {
            val result = method.invoke(null, 0L, 0L)
            expect(result == 0)
        }

        @JvmStatic
        fun testEqualityMinLong(method: Method) {
            val result = method.invoke(null, Long.MIN_VALUE, Long.MIN_VALUE)
            expect(result == 0)
        }

        @JvmStatic
        fun testEqualityMaxLong(method: Method) {
            val result = method.invoke(null, Long.MAX_VALUE, Long.MAX_VALUE)
            expect(result == 0)
        }
    }

    private class ClassVisitorImpl(apiVersion: Int, classVisitor: ClassVisitor) :
        ClassVisitor(apiVersion, classVisitor) {

        override fun visitMethod(
            access: Int, name: String, desc: String,
            signature: String?, exceptions: Array<out String>?
        ): MethodVisitor? {
            return if (name == "compare") {
                val methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions)
                MethodVisitorImpl(API_VERSION, methodVisitor)
            } else {
                cv.visitMethod(access, name, desc, signature, exceptions)
            }
        }
    }

    private class MethodVisitorImpl(apiVersion: Int, private val methodVisitor: MethodVisitor) :
        MethodVisitor(apiVersion, null) {

        override fun visitCode() {
            methodVisitor.visitCode()
            methodVisitor.visitVarInsn(Opcodes.LLOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.LLOAD, 2)
            methodVisitor.visitInsn(Opcodes.LCMP)
            methodVisitor.visitInsn(Opcodes.IRETURN)
            methodVisitor.visitMaxs(-1, -1)
            methodVisitor.visitEnd()
        }
    }

    private class LocalClassLoader : ClassLoader() {
        fun generateClass(name: String): Class<*> {
            val classReader = ClassReader(name)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            val classVisitor = ClassVisitorImpl(API_VERSION, classWriter)
            classReader.accept(classVisitor, 0)
            val byteCode = classWriter.toByteArray()
            return defineClass(name, byteCode, 0, byteCode.size)
        }
    }
}

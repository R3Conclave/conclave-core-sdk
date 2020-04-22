package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.djvm.proto.StringList
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class AnnotatedJavaClassTest {

    class SandboxAnnotationTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).
                    isEqualTo("@sandbox.com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation(value=Hello Java!)")
        }
    }

    class AnnotationInsideSandboxTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("@sandbox.com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation(value=Hello Java!)")
        }
    }


    class ReflectionCanFetchAllSandboxedAnnotationsTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).containsExactlyInAnyOrder(
                    "sandbox.com.r3.conclave.jvmtester.djvm.testsauxiliary.JavaAnnotation"
            )
        }
    }

    class ReflectionCanFetchAllMetaAnnotationsTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).containsExactlyInAnyOrder(
                    "java.lang.annotation.Retention",
                    "java.lang.annotation.Target"
            )
        }
    }
}
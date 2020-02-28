package com.r3.sgx.djvm.asserters

import com.r3.sgx.djvm.proto.StringList
import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class AnnotatedJavaClassTest {

    class SandboxAnnotationTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).
                    isEqualTo("@sandbox.com.r3.sgx.djvm.JavaAnnotation(value=Hello Java!)")
        }
    }

    class AnnotationInsideSandboxTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("@sandbox.com.r3.sgx.djvm.JavaAnnotation(value=Hello Java!)")
        }
    }


    class ReflectionCanFetchAllSandboxedAnnotationsTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).containsExactlyInAnyOrder(
                    "sandbox.com.r3.sgx.djvm.JavaAnnotation"
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
package com.r3.conclave.integrationtests.general.common.tasks

import avian.test.*

import com.r3.conclave.integrationtests.general.common.TestResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val testCases = mapOf<String, () -> Any?>(
    "AllFloats" to { AllFloats.main(emptyArray()) },
    "ArrayDequeTest" to { ArrayDequeTest.main(emptyArray()) },
    "ArraysTest" to { ArraysTest.main(emptyArray()) },
    "AtomicTests" to { AtomicTests.main(emptyArray()) },
    "BitsetTest" to { BitsetTest.main(emptyArray()) },
    "BufferedInputStreamTest" to { BufferedInputStreamTest.main(emptyArray()) },
    "Buffers" to { Buffers.main(emptyArray()) },
    "Busy" to { Busy.main(emptyArray()) },
    "Collections" to { Collections.main(emptyArray()) },
    "ConcurrentHashMapTest" to { ConcurrentHashMapTest.main(emptyArray()) },
    "Dates" to { Dates.main(emptyArray()) },
    "DequeHelper" to { DequeHelper.main(emptyArray()) },
    "DivideByZero" to { DivideByZero.main(emptyArray()) },
    "EnumSetTest" to { EnumSetTest.main(emptyArray()) },
    "Enums" to { Enums.main(emptyArray()) },
    "Exceptions" to { Exceptions.main(emptyArray()) },
    "Floats" to { Floats.main(emptyArray()) },
    "FormatStrings" to { FormatStrings.main(emptyArray()) },
    "GC" to { GC.main(emptyArray()) },
    "Hello" to { Hello.main(emptyArray()) },
    "Initializers" to { Initializers.main(emptyArray()) },
    "Integers" to { Integers.main(emptyArray()) },
    "InvokeDynamic" to { InvokeDynamic.main(emptyArray()) },
    "ListTest" to { avian.test.List.main(emptyArray()) },
    "Logging" to { Logging.main(emptyArray()) },
    "Longs" to { Longs.main(emptyArray()) },
    "MessageFormatTest" to { MessageFormatTest.main(emptyArray()) },
    "NullPointer" to { NullPointer.main(emptyArray()) },
    "Observe" to { Observe.main(emptyArray()) },
    "Proxies" to { Proxies.main(emptyArray()) },
    "QueueHelper" to { QueueHelper.main(emptyArray()) },
    "References" to { References.main(emptyArray()) },
    "Regex" to { Regex.main(emptyArray()) },
    "Simple" to { Simple.main(emptyArray()) },
    "StringBuilderTest" to { StringBuilderTest.main(emptyArray()) },
    "Strings" to { Strings.main(emptyArray()) },
    "Switch" to { Switch.main(emptyArray()) },
    "ThreadExceptions" to { ThreadExceptions.main(emptyArray()) },
    "Threads" to { Threads.main(emptyArray()) },
    "TimeUnitConversions" to { TimeUnitConversions.main(emptyArray()) },
    "Trace" to { Trace.main(emptyArray()) },
    "Tree" to { Tree.main(emptyArray()) },
    "UrlTest" to { UrlTest.main(emptyArray()) },
    "VirtualMethodsInheritance" to { VirtualMethodsInheritance.main(emptyArray()) },
)

// address these as part of https://r3-cev.atlassian.net/browse/CON-303
val disabledTestCases = mapOf<String, () -> Any?>(
    // disabled via jdk.serialFilter - CON-367
    "SerialFilterTest" to { SerialFilterTest.main(emptyArray()) },
    "Serialize" to { Serialize.main(emptyArray()) },

    // file IO - CON-366
    "FileOutput" to { FileOutput.main(emptyArray()) },
    "Files" to { Files.main(emptyArray()) },
    "FileSystemEmulation" to { FileSystemEmulation.main(emptyArray()) },
    "Zip" to { Zip.main(emptyArray()) },
    "ZipOutputStreamTest" to { ZipOutputStreamTest.main(emptyArray()) },

    // network access - CON-365
    // linker error - unresolved symbol 'epoll_*'
    // "Datagrams" to { Datagrams.main(emptyArray()) },
    //"Misc" to { Misc.main(emptyArray()) },
    //"Sockets" to { Sockets.main(emptyArray()) },

    // using reflection - CON-364
    // these won't compile using native-image
    // Error: com.oracle.svm.hosted.substitute.DeletedElementException: Unsupported method java.lang.ClassLoader.defineClass(String, byte[], int, int) is reachable
    // To diagnose the issue, you can add the option --report-unsupported-elements-at-runtime. The unsupported element is then reported at run time when it is accessed the first time.
    /*
    "Annotations" to { Annotations.main(emptyArray()) },
    "DefineClass" to { DefineClass.main(emptyArray()) },
    "LazyLoading" to { LazyLoading.main(emptyArray()) },
    "LongCompareTest" to { LongCompareTest.main(emptyArray()) },
    "Reflection" to { Reflection.main(emptyArray()) },
    "VerifyErrorTest" to { VerifyErrorTest.main(emptyArray()) },
     */

    // require(s) time - actually work, but time source is not trusted - CON-363
    "CompletionServiceTest" to { CompletionServiceTest.main(emptyArray()) },
    "FutureTaskTest" to { FutureTaskTest.main(emptyArray()) },
    "LinkedBlockingQueueTest" to { LinkedBlockingQueueTest.main(emptyArray()) },
    "MemoryRamp" to { MemoryRamp.main(emptyArray()) },

    // won't fix - running external binary is not supported
    //"Processes" to { Processes.main(emptyArray()) }

    // won't fix - deprecated
    // "Finalizers" to { Finalizers.main(emptyArray()) }
)

@Serializable
data class AvianTestRunner(val name: String) : JvmTestTask(), Deserializer<TestResult> {
    override fun run(context: RuntimeContext): ByteArray {
        val lambda = testCases[name] ?: return response(false, "test not found: $name")

        try {
            lambda()
        } catch (error: Throwable) {
            return response(false, "$error\n${error.stackTrace.joinToString("\n")}")
        }

        return response(true, name)
    }

    override fun deserialize(encoded: ByteArray): TestResult {
        return decode(encoded)
    }

    private fun response(success: Boolean, error: String): ByteArray {
        return Json.encodeToString(TestResult.serializer(), TestResult(success, error)).toByteArray()
    }
}

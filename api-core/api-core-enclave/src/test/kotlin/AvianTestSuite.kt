
import avian.test.VirtualMethodsInheritance
import avian.test.avian.OcallReadResourceBytes
import com.r3.conclave.core.common.BytesHandler
import com.r3.conclave.core.common.SimpleMuxingHandler
import com.r3.conclave.core.enclave.EnclaveApi
import com.r3.conclave.core.enclave.RootEnclave
import com.r3.conclave.dynamictesting.EnclaveBuilder
import com.r3.conclave.dynamictesting.EnclaveConfig
import com.r3.conclave.dynamictesting.EnclaveTestMode
import com.r3.conclave.dynamictesting.TestEnclavesBasedTest
import com.r3.conclave.testing.*
import org.junit.Test
import org.objectweb.asm.Opcodes
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.util.function.Consumer
import kotlin.test.assertEquals

class AvianTestSuite : TestEnclavesBasedTest(mode = EnclaveTestMode.Native) {

    companion object {
        val testCaseClasses = listOf(
                avian.test.FileSystemEmulation::class.java,
                avian.test.MessageFormatTest::class.java,
                avian.test.LazyLoading::class.java,
                avian.test.OutOfMemory::class.java,
                avian.test.AllFloats::class.java,
                avian.test.Annotations::class.java,
                avian.test.ArrayDequeTest::class.java,
                avian.test.ArraysTest::class.java,
                avian.test.AtomicTests::class.java,
                avian.test.BitsetTest::class.java,
                avian.test.Buffers::class.java,
                avian.test.Busy::class.java,
                avian.test.Collections::class.java,
                /* avian.test.CompletionServiceTest::class.java, NOT RUN: require time */
                avian.test.ConcurrentHashMapTest::class.java,
                /* avian.test.Datagrams::class.java, //< SKIP: not relevant in SGX */
                avian.test.Dates::class.java,
                avian.test.DefineClass::class.java,
                avian.test.DequeHelper::class.java,
                /* avian.test.DivideByZero::class.java, << FAIL: crash with SIGFPE */
                avian.test.Enums::class.java,
                avian.test.EnumSetTest::class.java,
                avian.test.Exceptions::class.java,
                /* avian.test.FileOutput::class.java, //< SKIP: not relevant in SGX */
                avian.test.Finalizers::class.java,
                avian.test.Floats::class.java,
                avian.test.FormatStrings::class.java,
                /* avian.test.FutureTaskTest::class.java, //< NOT RUN: requires current time */
                avian.test.GC::class.java,
                avian.test.Hello::class.java,
                avian.test.Initializers::class.java,
                avian.test.Integers::class.java,
                avian.test.InvokeDynamic::class.java,
                /* avian.test.LinkedBlockingQueueTest::class.java //< NOT RUN: requires current time */
                avian.test.List::class.java,
                avian.test.Logging::class.java,
                avian.test.Longs::class.java,
                avian.test.asm.LongCompareTest::class.java,
                /* avian.test.MemoryRamp::class.java, //< NOT RUN: requires current time */
                avian.test.Misc::class.java, //< Skip the parts involving network libraries
                /* avian.test.NullPointer::class.java //< FAIL */
                avian.test.Observe::class.java,
                /* avian.test.Processes::class.java, //< SKIP: not relevant in SGX */
                avian.test.Proxies::class.java,
                avian.test.QueueHelper::class.java,
                avian.test.References::class.java,
                /* avian.test.Reflection::class.java, //< FAIL */
                avian.test.Regex::class.java,
                /* avian.test.Serialize::class.java, //< FAIL */
                avian.test.Simple::class.java,
                /* avian.test.Sockets::class.java, //< SKIP: not relevant in SGX */
                avian.test.StackOverflow::class.java,
                avian.test.StringBuilderTest::class.java,
                avian.test.Strings::class.java,
                avian.test.Switch::class.java,
                /* avian.test.ThreadExceptions::class.java, //< FAIL */
                /* avian.test.Threads::class.java, //< FAIL */
                avian.test.TimeUnitConversions::class.java,
                avian.test.Tree::class.java,
                /* avian.test.Trace::class.java, //< FAIL*/
                avian.test.UrlTest::class.java,
//                avian.test.VerifyErrorTest::class.java  //< temporarily disabled
                avian.test.VirtualMethodsInheritance::class.java,
                avian.test.SerialFilterTest::class.java
                /* avian.test.Zip::class.java, //< NOT RUN: requires file I/O */
                /* avian.test.ZipOutputStreamTest::class.java //< NOT RUN: requires file I/O */
                )
    }

    class TestRunnerEnclave : RootEnclave() {
        override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
            mux.addDownstream(object : StringHandler() {
                override fun onReceive(sender: StringSender, testClassName: String) {
                    val testClass = Class.forName(testClassName)
                    val entry = testClass.getMethod("main", Array<String>::class.java)
                    try {
                        entry.invoke(null, emptyArray<String>())
                    } catch (e: InvocationTargetException) {
                        sender.send("FAIL")
                        throw e.getTargetException() ?: e
                    }
                    sender.send("SUCCESS")
                }
            })

            OcallReadResourceBytes.initialize(object : OcallReadResourceBytes() {
                val receiver = BytesRecordingHandler()
                val connection = mux.addDownstream(receiver)

                override fun readBytes_(path: String): ByteArray {
                    receiver.clear()
                    connection.send(ByteBuffer.wrap(path.toByteArray()))
                    if (receiver.size != 1) {
                        throw RuntimeException("Asking file $path by an ocall failed")
                    }
                    val response = receiver.nextCall
                    return ByteArray(response.remaining()).also {
                        response.get(it)
                    }
                }
            })
        }
    }

    class ResourceProviderHandler : BytesHandler() {
        override fun onReceive(connection: Connection, input: ByteBuffer) {
            val pathBytes = ByteArray(input.remaining())
            input.get(pathBytes)    
            val path = String(pathBytes)
            val response = File(this::class.java.getResource(path).file).readBytes()
            connection.send(ByteBuffer.wrap(response))
        }
    }

    @Test
    fun runAvianTestsInEnclave() {
        val builder = EnclaveBuilder(config = EnclaveConfig().withTCSNum(32),
                includeClasses = testCaseClasses + Opcodes::class.java)
        val handler = StringRecordingHandler()
        withEnclaveHandle(RootHandler(), TestRunnerEnclave::class.java, builder, Consumer { enclaveHandle ->
            val connection = enclaveHandle.connection.addDownstream(handler)
            enclaveHandle.connection.addDownstream(ResourceProviderHandler())
            for (testCase in testCaseClasses) {
                val msg = "Avian test case $testCase.name failed"
                connection.send(testCase.name)
                assertEquals(1, handler.calls.size, msg)
                assertEquals("SUCCESS", handler.calls[0], msg)
                handler.calls.clear()
                println("${testCase.name} PASSED")
            }
        })
    }
}

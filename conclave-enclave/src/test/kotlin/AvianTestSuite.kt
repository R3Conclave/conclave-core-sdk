import avian.test.avian.OcallReadResourceBytes
import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.utilities.internal.readFully
import com.r3.conclave.internaltesting.dynamic.EnclaveBuilder
import com.r3.conclave.internaltesting.dynamic.EnclaveConfig
import com.r3.conclave.internaltesting.dynamic.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.kotlin.callEnclave
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.objectweb.asm.Opcodes

class AvianTestSuite {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()

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

    class TestRunnerEnclave : EnclaveCall, Enclave() {
        init {
            OcallReadResourceBytes.initialize(object : OcallReadResourceBytes() {
                override fun readBytes_(path: String): ByteArray = callUntrustedHost(path.toByteArray())!!
            })
        }

        override fun invoke(bytes: ByteArray): ByteArray? {
            val testClass = Class.forName(String(bytes))
            val mainMethod = testClass.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, emptyArray<String>())
            return null
        }
    }

    @Test
    fun `avian tests in enclave`() {
        val host = testEnclaves.hostTo<TestRunnerEnclave>(EnclaveBuilder(
                config = EnclaveConfig().withTCSNum(32),
                includeClasses = testCaseClasses + Opcodes::class.java
        ))
        host.start(null, null, null)

        for (testCase in testCaseClasses) {
            // TODO Make this into a parameterised test when this module moves to Junit 5
            println("Testing ${testCase.name}")
            host.callEnclave(testCase.name.toByteArray()) {
                // Resource requests
                val path = String(it)
                requireNotNull(this::class.java.getResourceAsStream(path)?.readFully()) {
                    "Cannot find resource $path"
                }
            }
        }

        host.close()
    }
}

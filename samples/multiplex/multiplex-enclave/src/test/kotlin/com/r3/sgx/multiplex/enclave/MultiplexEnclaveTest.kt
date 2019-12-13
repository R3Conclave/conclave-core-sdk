package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.core.host.EnclaveletHostHandler
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.dynamictesting.EnclaveTestMode
import com.r3.sgx.dynamictesting.EnclaveTestMode.*
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.multiplex.client.MultiplexClientHandler
import com.r3.sgx.multiplex.common.SHA256_BYTES
import com.r3.sgx.multiplex.common.sha256
import com.r3.sgx.testing.HelperUtilities.expectWithin
import com.r3.sgx.testing.StringHandler
import com.r3.sgx.testing.StringSender
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.*

@RunWith(Parameterized::class)
class MultiplexEnclaveTest(mode: EnclaveTestMode) : TestEnclavesBasedTest(mode) {
    companion object {
        const val MESSAGE = "She sells sea shells on the sea shore."

        val multiplexPath: Path = Paths.get(mandatoryProperty("enclave_path"))
        val log: Logger = LoggerFactory.getLogger(MultiplexEnclaveTest::class.java)

        val attestationConfiguration = EpidAttestationHostConfiguration(
            quoteType = SgxQuoteType32.LINKABLE,
            spid = Cursor.allocate(SgxSpid)
        )
        fun createDynamicEnclaveletHostHandler() = EnclaveletHostHandler(attestationConfiguration)
    }

    private lateinit var enclaveHandle: EnclaveHandle<EnclaveletHostHandler.Connection>
    private lateinit var multiplexChannel: MultiplexClientHandler.Connection

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Before
    fun setupEnclave() {
        val handler = EnclaveletHostHandler(attestationConfiguration)
        enclaveHandle = createEnclaveWithHandler(handler, MultiplexEnclave::class.java, multiplexPath.toFile())
        val connection = enclaveHandle.connection

        if (mode == Native) {
            val quote = connection.attestation.getQuote()
            quote[quote.encoder.quote][SgxQuote.reportBody][SgxReportBody.reportData].read()
        }

        val (_, rootChannel) = connection.channels.addDownstream(MultiplexClientHandler()).get(10, SECONDS)
        multiplexChannel = rootChannel
    }

    @After
    fun freeEnclaves() {
        enclaveHandle.destroy()
        URLSchemes.clearURLs()
    }

    @Test
    fun testMultiplexEnclave() {
        val shouterFile = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
        val shouterBytes = shouterFile.toByteBuffer()
        val shouterHash = ByteBuffer.wrap(shouterBytes.sha256)
        val shoutingEnclave = multiplexChannel.loader.sendJar(shouterBytes, shouterHash).get(10, SECONDS)
        assertEquals(shouterHash, shoutingEnclave.enclaveHash)
        assertEquals(SHA256_BYTES, shoutingEnclave.enclaveHash.remaining())
        log.info("Shouting Enclave: {}", shoutingEnclave)

        var hasShouted = false
        val hostConnection = shoutingEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, shouter) = hostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                assertEquals(MESSAGE.toUpperCase(), string)
                log.info("Response: '{}'", string)
                hasShouted = true
            }
        }).get(10, SECONDS)
        shouter.send(MESSAGE)
        assertTrue(hasShouted)

        multiplexChannel.unloader.unload(shoutingEnclave)

        if (mode == Mock) {
            System.gc()
            assertDynamicEnclavesAreDeallocatedWithin(seconds = 20)
        }
    }

    @Test
    fun testUploadingEnclaveWithIncorrectHash() {
        exception.expect(RuntimeException::class.java)
        exception.expectMessage(IllegalArgumentException::class.java.name)
        exception.expectMessage("SHA-256 of enclave ")

        val shouterFile = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
        multiplexChannel.loader.sendJar(shouterFile.toByteBuffer(), ByteBuffer.allocate(SHA256_BYTES)).get(10, SECONDS)
    }

    @Test
    fun testRequestsToTwoDynamicEnclaves() {
        var hasShouted = false
        val shouterFile = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
        val shoutingEnclave = multiplexChannel.loader.sendJar(shouterFile.toByteBuffer()).get(10, SECONDS)
        val shoutingHostConnection = shoutingEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, shouter) = shoutingHostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                assertEquals(MESSAGE.toUpperCase(), string)
                log.info("Response: '{}'", string)
                hasShouted = true
            }
        }).get(10, SECONDS)

        var hasLisped = false
        val lisperFile = testEnclaves.getEnclaveJar(LispingEnclavelet::class.java)
        val lispingEnclave = multiplexChannel.loader.sendJar(lisperFile.toByteBuffer()).get(10, SECONDS)
        val lispingHostConnection = lispingEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, lisper) = lispingHostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                assertEquals("The thellth thea thellth on the thea thore.", string)
                log.info("Response: '{}'", string)
                hasLisped = true
            }
        }).get(10, SECONDS)

        shouter.send(MESSAGE)
        lisper.send(MESSAGE)

        assertTrue(hasShouted)
        assertTrue(hasLisped)
    }

    @Test
    fun testFailedDynamicRequest() {
        exception.expect(RuntimeException::class.java)
        exception.expectMessage(ThrowingEnclavelet.FailingException::class.java.name)
        exception.expectMessage("Disaster!")

        val throwerFile = testEnclaves.getEnclaveJar(ThrowingEnclavelet::class.java)
        val throwingEnclave = multiplexChannel.loader.sendJar(throwerFile.toByteBuffer()).get(10, SECONDS)
        val throwingHostConnection = throwingEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, thrower) = throwingHostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                fail("Should not reach here")
            }
        }).get(10, SECONDS)
        thrower.send("Disaster!")
    }

    @Test
    fun testLaunchingBrokenEnclavelet() {
        exception.expect(RuntimeException::class.java)
        exception.expectMessage(BrokenEnclavelet.BrokenException::class.java.name)
        exception.expectMessage("CANNOT START!")

        val brokenFile = testEnclaves.getEnclaveJar(BrokenEnclavelet::class.java)
        val brokenEnclave = multiplexChannel.loader.sendJar(brokenFile.toByteBuffer()).get(10, SECONDS)
        val brokenHostConnection = brokenEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        brokenHostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                fail("Should not reach here")
            }
        }).get(10, SECONDS)
    }

    @Test
    fun testLoadingGarbageInsteadOfJar() {
        exception.expect(RuntimeException::class.java)
        exception.expectMessage(IllegalStateException::class.java.name)
        exception.expectMessage("jar.manifest must not be null")

        assertEquals(0, URLSchemes.size)
        try {
            val garbage = ByteBuffer.wrap(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            multiplexChannel.loader.sendJar(garbage).get(10, SECONDS)
        } finally {
            if (mode == Mock) {
                // Check that garbage JAR's memory is released.
                System.gc()
                assertDynamicEnclavesAreDeallocatedWithin(seconds = 10)
            }
        }
    }

    @Test
    fun testSameEnclaveIsLoadedOnlyOnce() {
        assumeTrue("Requires enclavelet to run inside this JVM.", mode == Mock)

        val shouterFile = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
        val enclaveBytes = shouterFile.toByteBuffer()
        val enclave1 = multiplexChannel.loader.sendJar(enclaveBytes).get(10, SECONDS)
        val enclave2 = multiplexChannel.loader.sendJar(enclaveBytes).get(10, SECONDS)
        assertNotSame(enclave1, enclave2)
        assertEquals(enclave1.enclaveHash, enclave2.enclaveHash)
        assertEquals(1, URLSchemes.size)
    }

    @Test
    fun testRequestsToDifferentInstancesOfTheSameEnclave() {
        val shouterFile = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)

        var shout1Count = 0
        val shoutingEnclave1 = multiplexChannel.loader.sendJar(shouterFile.toByteBuffer()).get(10, SECONDS)
        val hostConnection1 = shoutingEnclave1.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, shouter1) = hostConnection1.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                assertEquals(MESSAGE.toUpperCase(), string)
                log.info("Response: '{}'", string)
                shout1Count += 1
            }
        }).get(10, SECONDS)

        var shout2Count = 0
        val shoutingEnclave2 = multiplexChannel.loader.sendJar(shouterFile.toByteBuffer()).get(10, SECONDS)
        val hostConnection2 = shoutingEnclave2.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, shouter2) = hostConnection2.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                assertEquals(MESSAGE.toUpperCase(), string)
                log.info("Response: '{}'", string)
                shout2Count += 10
            }
        }).get(10, SECONDS)

        assertNotEquals(shoutingEnclave1.id, shoutingEnclave2.id)
        assertEquals(shoutingEnclave1.enclaveHash, shoutingEnclave2.enclaveHash)

        shouter1.send(MESSAGE)
        shouter2.send(MESSAGE)

        assertEquals(1, shout1Count)
        assertEquals(10, shout2Count)
    }

    @Test
    fun testUsingEnclaveWeUploadedEarlier() {
        val shouterFile = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
        val initialEnclave = multiplexChannel.loader.sendJar(shouterFile.toByteBuffer()).get(10, SECONDS)
        log.info("Created Enclave: {}", initialEnclave)

        val shoutingEnclave = multiplexChannel.loader.useJar(initialEnclave.enclaveHash).get(10, SECONDS)
        log.info("Used Enclave: {}", shoutingEnclave)

        var hasShouted = false
        val hostConnection = shoutingEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        val (_, shouter) = hostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                assertEquals(MESSAGE.toUpperCase(), string)
                log.info("Response: '{}'", string)
                hasShouted = true
            }
        }).get(10, SECONDS)

        multiplexChannel.unloader.unload(initialEnclave)

        shouter.send(MESSAGE)
        assertTrue(hasShouted)

        multiplexChannel.unloader.unload(shoutingEnclave)

        if (mode == Mock) {
            System.gc()
            assertDynamicEnclavesAreDeallocatedWithin(seconds = 20)
        }
    }

    private fun assertDynamicEnclavesAreDeallocatedWithin(seconds: Int) {
        if (!expectWithin(seconds) { URLSchemes.size == 0 }) {
            fail("${URLSchemes.size} memory URL(s) still allocated.")
        }
    }
}

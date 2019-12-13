package com.r3.sgx.enclave.signing

import com.r3.sgx.core.common.*
import com.r3.sgx.core.common.attestation.AttestedSignatureVerifier
import com.r3.sgx.core.common.attestation.PublicKeyAttester
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.core.host.*
import com.r3.sgx.enclave.signing.internal.MyAMQPSerializationScheme
import com.r3.sgx.enclave.signing.internal.asContextEnv
import com.r3.sgx.testing.BytesRecordingHandler
import com.r3.sgx.testing.RootHandler
import com.r3.sgx.testing.TrustedSgxQuote
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.fail

class SigningEnclaveTest {

    private val enclavePath = System.getProperty("com.r3.sgx.enclave.path")
                                  ?: fail("System property 'com.r3.sgx.enclave.path' not set")

    private val handler = BytesRecordingHandler()
    private lateinit var configuration: EpidAttestationHostConfiguration
    private lateinit var handle: EnclaveHandle<RootHandler.Connection>

    @Before
    fun setupEnclave() {
        configuration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType32.LINKABLE,
                spid = Cursor.allocate(SgxSpid))

        handle = NativeHostApi(EnclaveLoadMode.SIMULATION).createEnclave(RootHandler(), File(enclavePath))
    }

    @After
    fun destroyEnclave() {
        @Suppress("DEPRECATION")
        handle.destroy()
    }

    @Test
    fun testEnclaveSignature() {
        val connection = handle.connection
        val channels = connection.addDownstream(ChannelInitiatingHandler())
        val attesting = connection.addDownstream(EpidAttestationHostHandler(configuration))
        val attestedQuote = TrustedSgxQuote.fromSignedQuote(attesting.getQuote())
        val enclaveSignatureVerifier = AttestedSignatureVerifier(
                SignatureSchemeId.EDDSA_ED25519_SHA512,
                PublicKeyAttester(attestedQuote))

        val encryptionInitiatingHandler = EncryptionInitiatingHandler(
                signatureVerifier = enclaveSignatureVerifier,
                protocolId = EncryptionProtocolId.ED25519_AESGCM128)

        val (_, channel) = channels.addDownstream(encryptionInitiatingHandler).get()
        val encryptedChannel = channel.initiate(handler)

        val stuff = Stuff(1, 2.3, listOf(1, 2, 3))

        val message = MyAMQPSerializationScheme.createSerializationEnv().asContextEnv {
            stuff.serialize(/*context = SerializationDefaults.P2P_CONTEXT*/)
        }.bytes

        encryptedChannel.send(ByteBuffer.wrap(message))

        assertEquals(1, handler.size)

        val responseByteBuffer = handler.nextCall
        val responseBytes = ByteArray(responseByteBuffer.remaining()).also {
            responseByteBuffer.get(it)
        }
        val response = MyAMQPSerializationScheme.createSerializationEnv().asContextEnv {
            SerializedBytes<SignedStuff>(responseBytes).deserialize()
        }

        assertEquals(stuff, response.stuff)

        enclaveSignatureVerifier.verify(
                enclaveSignatureVerifier.decodeAttestedKey(response.key),
                response.signature,
                message)
    }
}

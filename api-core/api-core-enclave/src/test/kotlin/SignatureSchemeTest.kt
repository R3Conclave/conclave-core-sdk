import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.core.common.crypto.internal.SignatureSchemeFactory
import org.junit.Test
import java.util.*
import kotlin.test.assertFails

class SignatureSchemeTest {
    @Test
    fun `eddsa scheme sign and verify`() {
        val eddsa = SignatureSchemeFactory.make(SignatureSchemeId.EDDSA_ED25519_SHA512)
        val keyPair = eddsa.generateKeyPair()
        val input = ByteArray(128).also { Random().nextBytes(it) }
        val signature = eddsa.sign(keyPair.private, input)
        val encodedKey = keyPair.public.encoded
        eddsa.verify(eddsa.decodePublicKey(encodedKey), signature, input)

        input[0] = ((input[0] + 1) % 256).toByte()

        assertFails {
            eddsa.verify(eddsa.decodePublicKey(encodedKey), signature, input)
        }
    }
}

import com.r3.conclave.filesystem.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class DefaultFileSystemProviderTest : FileSystemEnclaveTest() {
    @Test
    fun create() {
        val reply = request(type = Request.Type.DEFAULT_FILE_SYSTEM_PROVIDER_CREATE)
        assertThat(String(reply!!)).startsWith("com.r3.conclave.filesystem.jimfs.JimfsFileSystemProvider@")
    }
}
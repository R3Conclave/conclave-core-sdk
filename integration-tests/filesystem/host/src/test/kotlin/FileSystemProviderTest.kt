import com.r3.conclave.filesystem.proto.Request
import com.r3.conclave.filesystem.proto.StringList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileSystemProviderTest : FileSystemEnclaveTest() {
    @Test
    fun installedProviders() {
        val reply = request(type = Request.Type.FILE_SYSTEM_PROVIDER_INSTALLED_PROVIDERS)
        val valuesList = StringList.parseFrom(reply).valuesList
        assertThat(valuesList.size).isEqualTo(1)
        assertThat(valuesList[0]).startsWith("com.r3.conclave.filesystem.jimfs.JimfsFileSystemProvider@")
    }
}
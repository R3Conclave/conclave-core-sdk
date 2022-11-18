package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import java.nio.file.Path
import java.security.interfaces.RSAPublicKey
import kotlin.io.path.div

interface TestWithSigning : TaskTest {
    companion object {
        const val DUMMY_KEY_FILE_NAME = "dummy_key.pem"
    }

    val conclaveBuildDir: Path

    val dummyKeyFile: Path get() = conclaveBuildDir / "dummy_key.pem"

    fun dummyKey(): RSAPublicKey = TestUtils.readSigningKey(dummyKeyFile)
}

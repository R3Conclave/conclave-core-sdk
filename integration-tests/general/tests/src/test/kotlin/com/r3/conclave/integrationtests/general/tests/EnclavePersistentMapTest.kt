package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.GetPersistentMap
import com.r3.conclave.integrationtests.general.common.tasks.PutPersistentMap
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import org.assertj.core.api.Assertions.assertThat
import org.junitpioneer.jupiter.cartesian.CartesianTest
import org.junitpioneer.jupiter.cartesian.CartesianTest.Enum
import org.junitpioneer.jupiter.cartesian.CartesianTest.Values
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.random.Random

class EnclavePersistentMapTest : AbstractEnclaveActionTest(PERSISTING_ENCLAVE) {
    @CartesianTest
    fun `persistent map updated by several threads, with enclave restarts`(
        @Enum type: CallType,
        @Values(booleans = [false, true]) useKds: Boolean
    ) {
        this.useKds = useKds

        val threads = 10
        val mailClients = if (type == CallType.MAIL) Array(threads) { newMailClient() } else null
        val count = 200

        val executor = Executors.newCachedThreadPool()

        // Restart the enclave 3 times
        repeat(3) {
            // Each time have the threads concurrently update their entry 'count' times
            (0 until threads).map { threadIndex ->
                CompletableFuture.runAsync({
                    val key = "key${threadIndex + 1}"
                    val buffer = ByteBuffer.allocate(10240)
                    Random.nextBytes(buffer.array())
                    repeat(count) { repeatIndex ->
                        buffer.putInt(0, repeatIndex + 1)
                        val putAction = PutPersistentMap(key, buffer.array())
                        if (mailClients != null) {
                            mailClients[threadIndex].deliverMail(putAction)
                        } else {
                            callEnclave(putAction)
                        }
                    }
                }, executor)
            }.forEach { it.join() }

            restartEnclave()
        }

        val contents = (0 until threads).map { threadIndex ->
            val response = callEnclave(GetPersistentMap("key${threadIndex + 1}"))
            ByteBuffer.wrap(response.value).getInt()
        }
        assertThat(contents).containsOnly(count)
    }

    enum class CallType { MAIL, LOCAL }
}

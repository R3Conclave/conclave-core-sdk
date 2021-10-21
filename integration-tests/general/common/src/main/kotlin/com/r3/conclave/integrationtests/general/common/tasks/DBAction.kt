package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.DriverManager

@Serializable
abstract class DBAction<R> : EnclaveTestAction<R>() {
    companion object {
        const val URL: String = "jdbc:h2:/testdb"
    }

    fun getConnection(): Connection {
        return DriverManager.getConnection(URL)
    }
}
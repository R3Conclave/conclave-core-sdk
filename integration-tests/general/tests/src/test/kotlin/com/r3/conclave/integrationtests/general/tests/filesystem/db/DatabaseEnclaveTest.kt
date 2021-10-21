package com.r3.conclave.integrationtests.general.tests.filesystem.db

import com.r3.conclave.integrationtests.general.common.tasks.ExecuteSql
import com.r3.conclave.integrationtests.general.common.tasks.SqlQuery
import com.r3.conclave.integrationtests.general.tests.filesystem.FileSystemEnclaveTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseEnclaveTest : FileSystemEnclaveTest("com.r3.conclave.integrationtests.filesystem.db.enclave.DatabaseEnclave") {
    companion object {
        const val CREATE_CMD = "create table users(id  int(3) primary key, name varchar(20), email varchar(50), country varchar(20), password varchar(20))"
        const val INSERT_CMD_1 = "INSERT INTO users (id, name, email, country, password) VALUES " +
                "(1, 'FirstUser', 'first.user@r3.com', 'GB', 'password')"
        const val INSERT_CMD_2 = "INSERT INTO users (id, name, email, country, password) VALUES " +
                "(2, 'SecondUser', 'second.user@r3.com', 'GB', 'password')"
        const val SELECT_1 = "SELECT * FROM users WHERE ID = 1"
        const val SELECT_2 = "SELECT * FROM users WHERE country = 'GB'"
    }

    @Test
    fun `create, insert and select run correctly`() {
        callEnclave(ExecuteSql(CREATE_CMD))
        callEnclave(ExecuteSql(INSERT_CMD_1))
        val reply = callEnclave(SqlQuery(SELECT_1)).toString()
        assertThat(reply).isEqualTo("[(ID, 1), (NAME, FirstUser), (EMAIL, first.user@r3.com), (COUNTRY, GB)]")
    }

    @Test
    fun `selects work correctly after an enclave restart`() {
        callEnclave(ExecuteSql(CREATE_CMD))
        callEnclave(ExecuteSql(INSERT_CMD_1))
        val replyBefore = callEnclave(SqlQuery(SELECT_1)).toString()
        assertThat(replyBefore).isEqualTo("[(ID, 1), (NAME, FirstUser), (EMAIL, first.user@r3.com), (COUNTRY, GB)]")
        restartEnclave()
        callEnclave(ExecuteSql(INSERT_CMD_2))
        val replyAfter = callEnclave(SqlQuery(SELECT_1)).toString()
        assertThat(replyAfter).isEqualTo("[(ID, 1), (NAME, FirstUser), (EMAIL, first.user@r3.com), (COUNTRY, GB)]")
        val replyAll = callEnclave(SqlQuery(SELECT_2)).toString()
        assertThat(replyAll).isEqualTo("[(ID, 1), (NAME, FirstUser), (EMAIL, first.user@r3.com), (COUNTRY, GB), " +
                                        "(ID, 2), (NAME, SecondUser), (EMAIL, second.user@r3.com), (COUNTRY, GB)]")

    }
}

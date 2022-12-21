package com.r3.conclave.integrationtests.general.tests.filesystem.db

import com.r3.conclave.integrationtests.general.common.tasks.ExecuteSql
import com.r3.conclave.integrationtests.general.common.tasks.SqlQuery
import com.r3.conclave.integrationtests.general.tests.filesystem.FileSystemEnclaveTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DatabaseEnclaveTest : FileSystemEnclaveTest(DATABASE_ENCLAVE) {
    @ParameterizedTest
    @CsvSource(
        "false, false",
        "false, true",
        "true, false",
        "true, true"
    )
    fun `sql inside enclave`(restartBetweenCmds: Boolean, useKds: Boolean) {
        this.useKds = useKds

        callEnclave(ExecuteSql("""
            create table users(
                id int(3) primary key,
                name varchar(20),
                email varchar(50),
                country varchar(20),
                password varchar(20)
            )
            """))
        if (restartBetweenCmds) restartEnclave()

        callEnclave(ExecuteSql("""
            INSERT INTO users (id, name, email, country, password)
            VALUES (1, 'FirstUser', 'first.user@r3.com', 'GB', 'password')
            """))
        if (restartBetweenCmds) restartEnclave()

        assertFirstUserExists()
        if (restartBetweenCmds) restartEnclave()

        callEnclave(ExecuteSql("""
            INSERT INTO users (id, name, email, country, password)
            VALUES (2, 'SecondUser', 'second.user@r3.com', 'GB', 'password')
            """))
        if (restartBetweenCmds) restartEnclave()

        assertFirstUserExists()
        if (restartBetweenCmds) restartEnclave()

        val replyAll = callEnclave(SqlQuery("SELECT * FROM users WHERE country = 'GB'")).toString()
        assertThat(replyAll).isEqualTo("[(ID, 1), (NAME, FirstUser), (EMAIL, first.user@r3.com), (COUNTRY, GB), " +
                                        "(ID, 2), (NAME, SecondUser), (EMAIL, second.user@r3.com), (COUNTRY, GB)]")
    }

    private fun assertFirstUserExists() {
        val response = callEnclave(SqlQuery("SELECT * FROM users WHERE ID = 1")).toString()
        assertThat(response).isEqualTo("[(ID, 1), (NAME, FirstUser), (EMAIL, first.user@r3.com), (COUNTRY, GB)]")
    }
}

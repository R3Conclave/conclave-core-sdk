package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
class SqlQuery(private val sql: String) : DBAction<List<Pair<String, String>>>() {

    override fun run(context: EnclaveContext, isMail: Boolean): List<Pair<String, String>> {
        getConnection().use { con ->
            con.createStatement().use { stm ->
                stm.executeQuery(sql).use { rs ->
                    val rsmd = rs.metaData
                    val columnsNumber = rsmd.columnCount
                    val values = mutableListOf<Pair<String, String>>()

                    while (rs.next()) {
                        for (i in 1 until columnsNumber) {
                            values.add(Pair(rsmd.getColumnName(i), rs.getString(i)))
                        }
                    }
                    return values
                }
            }
        }
    }

    override fun resultSerializer(): KSerializer<List<Pair<String, String>>> {
        return ListSerializer(PairSerializer(String.serializer(), String.serializer()))
    }
}

@Serializable
class ExecuteSql(private val sql: String) : DBAction<Unit>() {

    override fun run(context: EnclaveContext, isMail: Boolean) {
        getConnection().use { con ->
            con.createStatement().use { stm ->
                stm.execute(sql)
            }
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

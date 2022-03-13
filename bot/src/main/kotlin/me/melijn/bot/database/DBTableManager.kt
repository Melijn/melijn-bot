package me.melijn.bot.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

open class DBTableManager<T : Table>(
    open val driverManager: DriverManager,
    val table: Table
) {

    inline fun <L> scopedTransaction(crossinline func: (Transaction) -> L): L = transaction(driverManager.database) {
        func(this)
    }

    fun newOrUpdate(insert: T.(InsertStatement<Number>) -> Unit, update: T.(UpdateBuilder<Int>) -> Unit) {
        return scopedTransaction {
            (table as T).insertOrUpdate(insert, update)
        }
    }
}
package me.melijn.bot.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

open class DBManager<ID : Comparable<ID>, T : IdTable<ID>, out K : Entity<ID>>(
    open val driverManager: DriverManager,
    val idTable: IdTable<ID>,
    val entityClass: EntityClass<ID, K>
) {


    fun get(id: ID): K? {
        return scopedTransaction {
            return@scopedTransaction entityClass.findById(id)
        }
    }

    inline fun <L> scopedTransaction(crossinline func: (Transaction) -> L): L = transaction(driverManager.database) {
        func(this)
    }


    fun new(init: K.() -> Unit): K = new(null, init)
    fun new(id: ID?, init: K.() -> Unit): K {
        return scopedTransaction {
            entityClass.new(id, init)
        }
    }

    fun update() {
//        scopedTransaction {
//            doUpdate(this)
//        }
    }

    fun newOrUpdate(insert: T.(InsertStatement<Number>) -> Unit, update: T.(UpdateBuilder<Int>) -> Unit) {
        return scopedTransaction {
            (idTable as T).insertOrUpdate(insert, update)
        }
    }
}

//fun <T : Comparable<T>> IdTable<T>.update(dbManager: DBManager<IdTable<T>>, doUpdate: (IdTable<T>) -> Unit) {
//    dbManager.scopedTransaction {
//        doUpdate(this)
//    }
//}


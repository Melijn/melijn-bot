package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object UserBalance : Table("user_balance") {

    val userId = long("user_id")
    val balance = long("balance")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}
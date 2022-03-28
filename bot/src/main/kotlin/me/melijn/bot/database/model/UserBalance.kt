package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@OptIn(ExperimentalUnsignedTypes::class)
@CreateTable
@Cacheable
object UserBalance : Table("user_balance") {

    val userId = ulong("user_id")

    val balance = long("balance")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}
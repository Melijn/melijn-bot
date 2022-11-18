package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object UserCommandCooldown : Table("user_command_cooldown") {

    val userId = ulong("user_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, commandId)

    init {
        index(true, userId, commandId)
    }
}
@CreateTable
@Cacheable
object UserCommandUsageHistory : Table("user_command_usage_history") {

    val userId = ulong("user_id")
    val commandId = integer("command_id")
    val usageHistory = binary("usage_history")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, commandId)

    init {
        index("user_cmd_idx", true, userId, commandId)
    }
}


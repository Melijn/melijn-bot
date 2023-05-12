package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import me.melijn.bot.model.enums.CommandState
import org.jetbrains.exposed.sql.Table

// example usages:
// guildId, null, null -> global state
// guildId, channelId, null -> channel command state
// guildId, null, roleId -> global role state
// guildId, channelId, userId -> channel specific user state

@CreateTable
@TableModel(true)
object CommandStates : Table("command_states") {
    val guildId = long("guild_id")

    // channelId, can be null
    val scopeId = long("scope_id").nullable()

    // roleId or userId
    val entityId = long("entity_id").nullable()
    // can be a category or * so you can disable a group of commands or just a command id (top name, group name, subcmdname)
    // disabling a top level command with children will automatically disable all the child commands ect.
    val commandsId = text("commands_id")

    // if muted the bot won't say: command is disabled
    val muted = bool("muted")

    // can be disabled or enabled
    val state = enumeration<CommandState>("command_state")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, scopeId, entityId, commandsId)
}
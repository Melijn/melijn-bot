package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

// example usages:
// guildId, null, null -> global state
// guildId, channelId, null -> channel command state
// guildId, null, roleId -> global role state
// guildId, channelId, userId -> channel specific user state

@CreateTable
@Cacheable
object CommandEmbedColor : Table("command_embed_color") {
    val guildId = ulong("guild_id").nullable()

    // channelId, can be null
    val scopeId = ulong("scope_id").nullable()

    // roleId or userId
    val entityId = ulong("entity_id").nullable()

    // can be a category or * so you can disable a group of commands or just a command id (top name, group name, subcmdname)
    // disabling a top level command with children will automatically disable all the child commands ect.
    val commandsId = text("commands_id")

    // can be disabled or enabled
    val color = integer("color")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, scopeId, entityId, commandsId)
}
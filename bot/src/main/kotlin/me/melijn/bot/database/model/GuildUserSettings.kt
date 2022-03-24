package me.melijn.bot.database.model

import me.melijn.annotationprocessors.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@OptIn(ExperimentalUnsignedTypes::class)
@CreateTable
@Cacheable<GuildUserSettings>
object GuildUserSettings : Table("guild_user") {

    var guildId = ulong("guild_id")
    var userId = ulong("user_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)
}
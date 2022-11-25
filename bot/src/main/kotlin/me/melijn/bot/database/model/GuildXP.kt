package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object GuildXP : Table("guild_xp") {

    var guildId = ulong("guild_id")
    var userId = ulong("user_id")
    var xp = ulong("xp")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)

    init {
        index(true, guildId, userId)
        index(false, guildId, xp)
    }
}
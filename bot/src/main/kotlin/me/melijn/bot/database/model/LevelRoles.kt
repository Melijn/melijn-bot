package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object LevelRoles : Table("level_roles") {

    var guildId = ulong("guild_id")
    var level = ulong("level")
    var roleId = ulong("role_id")
    var stay = ulong("stay")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, level)

    init {
        index(false, guildId)
        index(true, guildId, level)
    }
}
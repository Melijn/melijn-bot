package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object LevelRoles : Table("level_roles") {

    var guildId = ulong("guild_id")
    var level = ulong("level")
    var roleId = ulong("role_id")
    var stay = bool("stay")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, level)

    init {
        index(false, guildId)
        index(true, guildId, level)
    }
}
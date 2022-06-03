package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object TopRoles : Table("top_roles"){

    var guildId = ulong("guild_id")
    var memberAmount = ulong("member_amount")
    var roleId = ulong("role_id")
    var minLevelTop = ulong("min_level")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, roleId)

    init {
        index(false, guildId)
        index(true, guildId, roleId)
    }
}
package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object TopRoleMember : Table("top_role_member") {
    var guildId = ulong("guild_id")
    var userId = ulong("user_id")
    var roleId = ulong("role_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, roleId)

    init {
        index(false, guildId, roleId)
        index(true, guildId, roleId)
    }
}
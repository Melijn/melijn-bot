package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object TopRoleMember : Table("top_role_member") {

    var guildId = long("guild_id")
    var userId = long("user_id")
    var roleId = long("role_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, roleId)

    init {
        index(false, guildId, roleId)
        index(true, guildId, roleId)
    }
}
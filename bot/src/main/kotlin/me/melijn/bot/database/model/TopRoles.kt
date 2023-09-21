package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object TopRoles : Table("top_roles"){

    var guildId = long("guild_id")
    var memberCount = integer("member_count")
    var roleId = long("role_id")
    var minLevelTop = long("min_level")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, roleId)

    init {
        index(false, guildId)
        index(true, guildId, roleId)
    }
}
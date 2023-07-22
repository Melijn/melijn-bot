package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object TopRoles : Table("top_roles"){

    var guildId = long("guild_id")
    var memberAmount = integer("member_amount")
    var roleId = long("role_id")
    var minLevelTop = integer("min_level")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, roleId)

    init {
        index(false, guildId)
        index(true, guildId, roleId)
    }
}
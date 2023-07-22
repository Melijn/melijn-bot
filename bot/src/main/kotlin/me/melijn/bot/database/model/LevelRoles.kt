package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object LevelRoles : Table("level_roles") {

    var guildId = long("guild_id")
    var level = long("level")
    var roleId = long("role_id")
    var stay = bool("stay")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, level)

    init {
        index(false, guildId)
        index(true, guildId, level)
    }
}
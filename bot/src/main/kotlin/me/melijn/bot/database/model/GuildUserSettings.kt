package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object GuildUserSettings : Table("guild_user") {

    var guildId = long("guild_id")
    var userId = long("user_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)
}
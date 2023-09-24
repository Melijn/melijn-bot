package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import me.melijn.bot.commands.thirdparty.GameMode
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object OsuLink : Table("osu_link") {

    val userId = long("user_id")
    val osuId = integer("osu_id").nullable()
    val modePreference = enumeration<GameMode>("preferred_game_mode").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}
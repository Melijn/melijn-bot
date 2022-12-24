package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import me.melijn.bot.commands.GameMode
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object OsuLink : Table("osu_link") {

    val userId = ulong("user_id")
    val osuId = integer("osu_id").nullable()
    val modePreference = enumeration<GameMode>("preferred_game_mode").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}
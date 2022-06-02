package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import me.melijn.bot.commands.AniListLanguagePreference
import org.jetbrains.exposed.sql.Table

@OptIn(ExperimentalUnsignedTypes::class)
@CreateTable
@Cacheable
object OsuLink : Table("osu_link") {

    val userId = ulong("user_id")
    val osuId = integer("osu_id").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

}
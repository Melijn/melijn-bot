package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import me.melijn.bot.commands.AniListLanguagePreference
import org.jetbrains.exposed.sql.Table

@OptIn(ExperimentalUnsignedTypes::class)
@CreateTable
@Cacheable
object AnilistLink : Table("anilist_link") {

    val userId = ulong("user_id")
    val anilistId = integer("anilist_id").nullable()
    val preference = enumeration<AniListLanguagePreference>("preference").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

}
package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import me.melijn.bot.commands.AniListLanguagePreference
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object AnilistLink : Table("anilist_link") {

    val userId = long("user_id")
    val anilistId = integer("anilist_id").nullable()
    val preference = enumeration<AniListLanguagePreference>("preference").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

}
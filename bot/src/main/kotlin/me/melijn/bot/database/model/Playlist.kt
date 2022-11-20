package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@Cacheable
object Playlist : Table("playlist") {

    val playlistId = uuid("id")
    val userId = ulong("user_id")
    val created = datetime("created")
    val name = text("name")
    val public = bool("public")

    override val primaryKey: PrimaryKey = PrimaryKey(playlistId)

    init {
        index(true, playlistId)
        index(false, userId)
        index(true, userId, name)
    }
}
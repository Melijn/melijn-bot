package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@Cacheable
object Playlist : Table("playlist") {

    val userId = ulong("user_id")
    val created = datetime("created")
    val name = text("name")
    val public = bool("public")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, created)

    init {
        index(false, userId)
        index(true, userId, name)
    }
}
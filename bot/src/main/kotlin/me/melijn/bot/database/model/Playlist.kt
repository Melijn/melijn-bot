package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@TableModel(true)
object Playlist : Table("playlist") {

    val playlistId = uuid("id")
    val userId = long("user_id")
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
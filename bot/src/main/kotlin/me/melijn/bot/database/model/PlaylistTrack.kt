package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@Cacheable
object PlaylistTrack : Table("playlist_track") {

    val playlistOwner = ulong("playlist_user_id")
    val playlistCreated = datetime("playlist_created")

    val track = text("track_base64")

    val addedTime = datetime("added_time")

    override val primaryKey: PrimaryKey = PrimaryKey(playlistOwner, playlistCreated, track, addedTime)

    init {
        index(false, playlistOwner, playlistCreated)
    }

}
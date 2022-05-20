package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@Cacheable
object PlaylistTrack : Table("playlist_track") {

    val playlistId = reference("playlist", Playlist.playlistId)
    val addedTime = datetime("added_time")
    val trackId = uuid("track_id").references(Track.trackId)

    override val primaryKey: PrimaryKey = PrimaryKey(trackId)

    init {
        index(true, playlistId, trackId)
    }
}
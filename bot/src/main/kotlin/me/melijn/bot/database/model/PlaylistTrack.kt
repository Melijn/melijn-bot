package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import me.melijn.bot.model.TrackSource
import me.melijn.bot.music.TrackType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.kotlin.datetime.duration

@CreateTable
@Cacheable
object PlaylistTrack : Table("playlist_track") {

    val playlistId = reference("playlist", Playlist.playlistId)
    val trackId = uuid("playlist_track_id")

    // mutual track data
    val title = text("title")
    val url = text("url")
    val isStream = bool("is_stream")
    val length = duration("length")

    val trackType = enumeration("track_type", TrackType::class)
    val addedTime = datetime("added_time")

    override val primaryKey: PrimaryKey = PrimaryKey(trackId)

    init {
        index(true, trackId)
        index(true, playlistId, trackId)
    }

}

@CreateTable
@Cacheable
object PlaylistFetchedTrack : Table("playlist_fetched_track") {

    val trackId = reference("playlist_track_id", PlaylistTrack.trackId)

    val trackBase64 = text("track_base64")
    val author= text("author").nullable()
    val identifier= text("identifier").nullable()
    val trackSource= enumeration("track_source", TrackSource::class)
    val trackInfoVersion = integer("track_info_version")

    override val primaryKey: PrimaryKey = PrimaryKey(trackId)

    init {
        index(true, trackId)
    }
}

@CreateTable
@Cacheable
object PlaylistSpotifyTrack : Table("playlist_spotify_track") {

    val trackId = reference("playlist_track_id", PlaylistTrack.trackId)

    val author= text("author")
    val identifier= text("identifier")

    override val primaryKey: PrimaryKey = PrimaryKey(trackId)

    init {
        index(true, trackId)
    }
}
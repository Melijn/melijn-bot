package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import me.melijn.bot.model.TrackSource
import me.melijn.bot.music.TrackType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.duration

@CreateTable
@TableModel(true)
object Track : Table("track") {

    val trackId = uuid("track_id")

    // mutual track data
    val title = text("title")
    val url = text("url")
    val isStream = bool("is_stream")
    val length = duration("length")

    val trackType = enumeration("track_type", TrackType::class)

    override val primaryKey: PrimaryKey = PrimaryKey(trackId)

    init {
        index(true, trackId)
        index(true, title, url, isStream, length, trackType)
    }
}

@CreateTable
@TableModel(true)
object FetchedTrack : TrackJoinTable("fetched_track") {

    override val trackId = reference("track_id", Track.trackId)

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
@TableModel(true)
object SpotifyTrack : TrackJoinTable("spotify_track") {

    override val trackId = reference("track_id", Track.trackId)

    val author= text("author")
    val identifier= text("identifier")

    override val primaryKey: PrimaryKey = PrimaryKey(trackId)

    init {
        index(true, trackId)
    }
}
package me.melijn.bot.database.manager

import kotlinx.datetime.LocalDateTime
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.model.PartialUser
import me.melijn.bot.music.*
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.gen.FetchedTrackData
import me.melijn.gen.PlaylistData
import me.melijn.gen.PlaylistTrackData
import me.melijn.gen.SpotifyTrackData
import me.melijn.gen.database.manager.AbstractFetchedTrackManager
import me.melijn.gen.database.manager.AbstractPlaylistTrackManager
import me.melijn.gen.database.manager.AbstractSpotifyTrackManager
import me.melijn.gen.database.manager.AbstractTrackManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.utils.TimeUtil
import org.jetbrains.exposed.sql.*
import java.util.*
import me.melijn.bot.database.model.FetchedTrack as DBFetchedTrack
import me.melijn.bot.database.model.SpotifyTrack as DBSpotifyTrack
import me.melijn.bot.database.model.Track as DBTrack

@Inject
class PlaylistTrackManager(override val driverManager: DriverManager) : AbstractPlaylistTrackManager(driverManager) {

    private val trackManager by inject<TrackManager>()
    private val playlistFetchedTrackManager by inject<PlaylistFetchedTrackManager>()
    private val playlistSpotifyTrackManager by inject<PlaylistSpotifyTrackManager>()

    fun newTrack(playlist: PlaylistData, track: Track) {
        val trackId = track.run {
            trackManager.getByIndex1(title, url, isStream, length, track.sourceType.trackType)
        }?.trackId ?: UUID.randomUUID()

        trackManager.store(me.melijn.gen.TrackData(
            trackId,
            track.title,
            track.url,
            track.isStream,
            track.length,
            track.sourceType.trackType
        ))

        when (track) {
            is SpotifyTrack -> playlistSpotifyTrackManager.store(
                SpotifyTrackData(trackId, track.author, track.identifier)
            )
            is FetchedTrack -> playlistFetchedTrackManager.store(
                FetchedTrackData(
                    trackId,
                    track.track,
                    track.author,
                    track.identifier,
                    track.sourceType,
                    track.trackInfoVersion.toInt()
                )
            )
        }

        store(
            PlaylistTrackData(
                playlist.playlistId,
                TimeUtil.localDateTimeNow(),
                trackId
            )
        )
    }

    fun getTracksInPlaylist(playlist: PlaylistData): List<Pair<PlaylistTrackData, me.melijn.gen.TrackData>> {
        return scopedTransaction {
            PlaylistTrack.join(DBTrack, JoinType.INNER) {
                PlaylistTrack.trackId.eq(DBTrack.trackId)
            }.select {
                PlaylistTrack.playlistId.eq(playlist.playlistId)
            }.map {
                PlaylistTrackData.fromResRow(it) to me.melijn.gen.TrackData.fromResRow(it)
            }.sortedBy { it.first.trackId }.sortedBy { it.first.addedTime }
        }
    }

    fun getMelijnTracksInPlaylist(playlist: PlaylistData, requester: PartialUser): List<Track> {
        val sortableTracks = mutableListOf<Triple<UUID, LocalDateTime, Track>>()
        scopedTransaction {
            PlaylistTrack.join(DBTrack, JoinType.INNER) {
                DBTrack.trackType.eq(TrackType.FETCHED)
                    .and(PlaylistTrack.trackId.eq(DBTrack.trackId))
                    .and(PlaylistTrack.playlistId.eq(playlist.playlistId))
            }.join(DBFetchedTrack, JoinType.INNER) {
                DBTrack.trackId.eq(DBFetchedTrack.trackId)
            }.selectAll().forEach {
                sortableTracks.add(
                    Triple(
                        it[PlaylistTrack.trackId], it[PlaylistTrack.addedTime], FetchedTrack(
                            it[DBFetchedTrack.trackBase64],
                            it[DBTrack.title],
                            it[DBFetchedTrack.author],
                            it[DBTrack.url],
                            it[DBFetchedTrack.identifier],
                            it[DBTrack.isStream],
                            TrackData.fromNow(requester, it[DBFetchedTrack.identifier]),
                            it[DBTrack.length],
                            it[DBFetchedTrack.trackSource],
                            it[DBFetchedTrack.trackInfoVersion].toByte(),
                        )
                    )
                )
            }
            PlaylistTrack.join(DBTrack, JoinType.INNER) {
                DBTrack.trackType.eq(TrackType.SPOTIFY)
                    .and(PlaylistTrack.trackId.eq(DBTrack.trackId))
                    .and(PlaylistTrack.playlistId.eq(playlist.playlistId))
            }.join(DBSpotifyTrack, JoinType.INNER) {
                DBTrack.trackId.eq(DBSpotifyTrack.trackId)
            }.selectAll().forEach {
                sortableTracks.add(
                    Triple(
                        it[DBTrack.trackId], it[PlaylistTrack.addedTime], SpotifyTrack(
                            it[DBTrack.title],
                            it[DBSpotifyTrack.author],
                            it[DBTrack.url],
                            it[DBSpotifyTrack.identifier],
                            it[DBTrack.isStream],
                            TrackData.fromNow(requester, it[DBSpotifyTrack.identifier]),
                            it[DBTrack.length]
                        )
                    )
                )
            }
        }
        return sortableTracks.sortedBy { it.first }.sortedBy { it.second }.map { it.third }
    }

    fun deleteAll(tracks: List<PlaylistTrackData>) {
        scopedTransaction {
            for (track in tracks)
                PlaylistTrack.deleteWhere { PlaylistTrack.trackId.eq(track.trackId) }
        }
    }

    fun getTrackCount(existingPlaylist: PlaylistData): Long {
        return scopedTransaction {
            PlaylistTrack.select {
                PlaylistTrack.playlistId.eq(existingPlaylist.playlistId)
            }.count()
        }
    }
}

@Inject
class TrackManager(driverManager: DriverManager) : AbstractTrackManager(driverManager)

@Inject
class PlaylistSpotifyTrackManager(driverManager: DriverManager) : AbstractSpotifyTrackManager(driverManager)

@Inject
class PlaylistFetchedTrackManager(driverManager: DriverManager) : AbstractFetchedTrackManager(driverManager)
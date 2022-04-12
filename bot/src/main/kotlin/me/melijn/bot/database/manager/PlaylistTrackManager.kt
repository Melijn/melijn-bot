package me.melijn.bot.database.manager

import kotlinx.datetime.LocalDateTime
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.PlaylistFetchedTrack
import me.melijn.bot.database.model.PlaylistSpotifyTrack
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.model.PartialUser
import me.melijn.bot.music.*
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.gen.PlaylistData
import me.melijn.gen.PlaylistFetchedTrackData
import me.melijn.gen.PlaylistSpotifyTrackData
import me.melijn.gen.PlaylistTrackData
import me.melijn.gen.database.manager.AbstractPlaylistFetchedTrackManager
import me.melijn.gen.database.manager.AbstractPlaylistSpotifyTrackManager
import me.melijn.gen.database.manager.AbstractPlaylistTrackManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.utils.TimeUtil
import org.jetbrains.exposed.sql.*
import java.util.*

@Inject
class PlaylistTrackManager(override val driverManager: DriverManager) : AbstractPlaylistTrackManager(driverManager) {

    private val playlistFetchedTrackManager by inject<PlaylistFetchedTrackManager>()
    private val playlistSpotifyTrackManager by inject<PlaylistSpotifyTrackManager>()

    fun newTrack(playlist: PlaylistData, track: Track) {
        val trackId = UUID.randomUUID()
        store(
            PlaylistTrackData(
                playlist.playlistId,
                trackId,
                track.title,
                track.url,
                track.isStream,
                track.length,
                track.sourceType.trackType,
                TimeUtil.localDateTimeNow()
            )
        )
        when (track) {
            is SpotifyTrack -> playlistSpotifyTrackManager.store(
                PlaylistSpotifyTrackData(trackId, track.author, track.identifier)
            )
            is FetchedTrack -> playlistFetchedTrackManager.store(
                PlaylistFetchedTrackData(
                    trackId,
                    track.track,
                    track.author,
                    track.identifier,
                    track.sourceType,
                    track.trackInfoVersion.toInt()
                )
            )
        }
    }

    fun getTracksInPlaylist(playlist: PlaylistData): List<PlaylistTrackData> {
        return scopedTransaction {
            PlaylistTrack.select {
                PlaylistTrack.playlistId.eq(playlist.playlistId)
            }.map {
                PlaylistTrackData.fromResRow(it)
            }.sortedBy { it.trackId }.sortedBy { it.addedTime }
        }
    }

    fun getMelijnTracksInPlaylist(playlist: PlaylistData, requester: PartialUser): List<Track> {
        val sortableTracks = mutableListOf<Triple<UUID, LocalDateTime, Track>>()
        scopedTransaction {
            PlaylistTrack.join(PlaylistFetchedTrack, JoinType.INNER) {
                PlaylistTrack.trackType.eq(TrackType.FETCHED)
                    .and(PlaylistTrack.trackId.eq(PlaylistFetchedTrack.trackId))
                    .and(PlaylistTrack.playlistId.eq(playlist.playlistId))
            }.selectAll().forEach {
                sortableTracks.add(
                    Triple(
                        it[PlaylistTrack.trackId], it[PlaylistTrack.addedTime], FetchedTrack(
                            it[PlaylistFetchedTrack.trackBase64],
                            it[PlaylistTrack.title],
                            it[PlaylistFetchedTrack.author],
                            it[PlaylistTrack.url],
                            it[PlaylistFetchedTrack.identifier],
                            it[PlaylistTrack.isStream],
                            TrackData.fromNow(requester, it[PlaylistFetchedTrack.identifier]),
                            it[PlaylistTrack.length],
                            it[PlaylistFetchedTrack.trackSource],
                            it[PlaylistFetchedTrack.trackInfoVersion].toByte(),
                        )
                    )
                )
            }
            PlaylistTrack.join(PlaylistSpotifyTrack, JoinType.INNER) {
                PlaylistTrack.trackId.eq(PlaylistSpotifyTrack.trackId)
            }.select {
                PlaylistTrack.playlistId.eq(playlist.playlistId)
                    .and(PlaylistTrack.trackType.eq(TrackType.SPOTIFY))
            }.forEach {
                sortableTracks.add(
                    Triple(
                        it[PlaylistTrack.trackId], it[PlaylistTrack.addedTime], SpotifyTrack(
                            it[PlaylistTrack.title],
                            it[PlaylistSpotifyTrack.author],
                            it[PlaylistTrack.url],
                            it[PlaylistSpotifyTrack.identifier],
                            it[PlaylistTrack.isStream],
                            TrackData.fromNow(requester, it[PlaylistSpotifyTrack.identifier]),
                            it[PlaylistTrack.length]
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
class PlaylistSpotifyTrackManager(driverManager: DriverManager) : AbstractPlaylistSpotifyTrackManager(driverManager)

@Inject
class PlaylistFetchedTrackManager(driverManager: DriverManager) : AbstractPlaylistFetchedTrackManager(driverManager)
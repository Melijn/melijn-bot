package me.melijn.bot.database.manager

import kotlinx.datetime.LocalDateTime
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.PlaylistFetchedTrack
import me.melijn.bot.database.model.PlaylistSpotifyTrack
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.model.PartialUser
import me.melijn.bot.music.*
import me.melijn.gen.PlaylistData
import me.melijn.gen.PlaylistTrackData
import me.melijn.gen.database.manager.AbstractPlaylistTrackManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.utils.TimeUtil
import org.jetbrains.exposed.sql.*
import java.util.*

@Inject
class PlaylistTrackManager(override val driverManager: DriverManager) : AbstractPlaylistTrackManager(driverManager) {

    fun newTrack(playlist: PlaylistData, track: Track) {
        store(
            PlaylistTrackData(
                playlist.playlistId,
                UUID.randomUUID(),
                track.title,
                track.url,
                track.isStream,
                track.length,
                track.sourceType.trackType,
                TimeUtil.localDateTimeNow()
            )
        )
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
        return scopedTransaction {
            val sortableTracks = mutableListOf<Triple<UUID, LocalDateTime, Track>>()
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
            PlaylistTrack.join(PlaylistFetchedTrack, JoinType.INNER) {
                PlaylistTrack.trackType.eq(TrackType.SPOTIFY)
                    .and(PlaylistTrack.trackId.eq(PlaylistFetchedTrack.trackId))
            }.selectAll().forEach {
                sortableTracks.add(
                    Triple(
                        it[PlaylistTrack.trackId], it[PlaylistTrack.addedTime], SpotifyTrack(
                            it[PlaylistTrack.title],
                            it[PlaylistSpotifyTrack.author],
                            it[PlaylistTrack.url],
                            it[PlaylistSpotifyTrack.identifier],
                            it[PlaylistTrack.isStream],
                            TrackData.fromNow(requester, it[PlaylistFetchedTrack.identifier]),
                            it[PlaylistTrack.length]
                        )
                    )
                )
            }
            sortableTracks.sortedBy { it.first }.sortedBy { it.second }.map { it.third }
        }
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
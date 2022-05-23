package me.melijn.bot.database.manager

import kotlinx.datetime.LocalDateTime
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.database.model.TrackJoinTable
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*
import me.melijn.bot.database.model.FetchedTrack as DBFetchedTrack
import me.melijn.bot.database.model.SpotifyTrack as DBSpotifyTrack
import me.melijn.bot.database.model.Track as DBTrack
import me.melijn.gen.TrackData as GenTrackData

@Inject
class PlaylistTrackManager(override val driverManager: DriverManager) : AbstractPlaylistTrackManager(driverManager) {

    private val trackManager by inject<TrackManager>()

    fun newTrack(playlist: PlaylistData, track: Track) {
        val trackId = trackManager.storeMusicTrack(track)
        store(PlaylistTrackData(playlist.playlistId, TimeUtil.localDateTimeNow(), trackId))
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
        val sortableTracks: SortableTracks = mutableListOf()
        val where = { trackType: TrackType ->
            DBTrack.trackType.eq(trackType)
                .and(PlaylistTrack.trackId.eq(DBTrack.trackId))
                .and(PlaylistTrack.playlistId.eq(playlist.playlistId))
        }
        val trackCollector = { it: ResultRow, trackType: TrackType ->
            val track = when (trackType) {
                TrackType.SPOTIFY -> trackManager.spotifyTrackFromResRow(it, requester)
                TrackType.FETCHED -> trackManager.fetchedTrackFromResRow(it, requester)
            }
            sortableTracks.add(Triple(it[PlaylistTrack.trackId], it[PlaylistTrack.addedTime], track))
        }
        trackManager.joinAllTypesInto(PlaylistTrack, where, trackCollector)
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

private typealias SortableTracks = MutableList<Triple<UUID, LocalDateTime, Track>>

@Inject
class TrackManager(
    driverManager: DriverManager,
    val spotifyTrackManager: SpotifyTrackManager,
    val fetchedTrackManager: FetchedTrackManager
) : AbstractTrackManager(driverManager) {

    fun storeMusicTrack(musicTrack: Track) = musicTrack.run {
        val trackId = getByIndex1(title, url, isStream, length, sourceType.trackType)?.trackId ?: UUID.randomUUID()

        store(GenTrackData(trackId, title, url, isStream, length, sourceType.trackType))

        when (this) {
            is SpotifyTrack -> spotifyTrackManager.store(SpotifyTrackData(trackId, author, identifier))
            is FetchedTrack -> fetchedTrackManager.store(
                FetchedTrackData(trackId, track, author, identifier, sourceType, trackInfoVersion.toInt())
            )
        }

        return@run trackId
    }

    fun spotifyTrackFromResRow(it: ResultRow, requester: PartialUser): SpotifyTrack {
        return SpotifyTrack(
            it[DBTrack.title], it[DBSpotifyTrack.author], it[DBTrack.url], it[DBSpotifyTrack.identifier],
            it[DBTrack.isStream], TrackData.fromNow(requester, it[DBSpotifyTrack.identifier]), it[DBTrack.length]
        )
    }

    fun fetchedTrackFromResRow(it: ResultRow, requester: PartialUser): FetchedTrack {
        return FetchedTrack(
            it[DBFetchedTrack.trackBase64], it[DBTrack.title], it[DBFetchedTrack.author], it[DBTrack.url],
            it[DBFetchedTrack.identifier], it[DBTrack.isStream],
            TrackData.fromNow(requester, it[DBFetchedTrack.identifier]), it[DBTrack.length],
            it[DBFetchedTrack.trackSource], it[DBFetchedTrack.trackInfoVersion].toByte()
        )
    }

    fun joinAllTypesInto(
        table: TrackJoinTable,
        where: (TrackType) -> Op<Boolean>, trackCollector: (ResultRow, TrackType) -> Boolean
    ) {
        scopedTransaction {
            for (trackType in TrackType.values()) {
                val trackTypeTable: TrackJoinTable = when (trackType) {
                    TrackType.SPOTIFY -> DBSpotifyTrack
                    TrackType.FETCHED -> DBFetchedTrack
                }
                table.join(DBTrack, JoinType.INNER) {
                    where(trackType)
                }.join(trackTypeTable, JoinType.INNER) {
                    DBTrack.trackId.eq(trackTypeTable.trackId)
                }.selectAll().forEach {
                    trackCollector(it, trackType)
                }
            }
        }
    }
}

@Inject
class SpotifyTrackManager(driverManager: DriverManager) : AbstractSpotifyTrackManager(driverManager)

@Inject
class FetchedTrackManager(driverManager: DriverManager) : AbstractFetchedTrackManager(driverManager)
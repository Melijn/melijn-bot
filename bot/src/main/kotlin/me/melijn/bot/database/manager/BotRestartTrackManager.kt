package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import kotlinx.datetime.toLocalDateTime
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.BotRestartTrackEntry
import me.melijn.bot.database.model.BotRestartTrackQueue
import me.melijn.bot.model.PartialUser
import me.melijn.bot.model.PodInfo
import me.melijn.bot.music.FetchedTrack
import me.melijn.bot.music.SpotifyTrack
import me.melijn.bot.music.Track
import me.melijn.bot.music.TrackType
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.gen.*
import me.melijn.gen.database.manager.AbstractBotRestartTrackEntryManager
import me.melijn.gen.database.manager.AbstractBotRestartTrackQueueManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.utils.TimeUtil
import org.jetbrains.exposed.sql.*
import java.util.*

@Inject
class BotRestartTrackEntryManager(driverManager: DriverManager) : AbstractBotRestartTrackEntryManager(driverManager) {

    private val trackManager by KoinUtil.inject<TrackManager>()
    private val playlistFetchedTrackManager by KoinUtil.inject<PlaylistFetchedTrackManager>()
    private val playlistSpotifyTrackManager by KoinUtil.inject<PlaylistSpotifyTrackManager>()
    private val setttings by KoinUtil.inject<Settings>()

    fun newTrack(guildId: ULong, position: Int, track: Track) {
        val trackId = track.run {
            trackManager.getByIndex1(title, url, isStream, length, track.sourceType.trackType)
        }?.trackId ?: UUID.randomUUID()

        trackManager.store(
            TrackData(
                trackId,
                track.title,
                track.url,
                track.isStream,
                track.length,
                track.sourceType.trackType
            )
        )

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
            BotRestartTrackEntryData(
                guildId,
                trackId,
                position,
                track.data?.requester?.idULong ?: setttings.bot.id.toULong(),
                track.data?.requestedAt?.toLocalDateTime(kotlinx.datetime.TimeZone.UTC) ?: TimeUtil.localDateTimeNow()
            )
        )
    }

    fun getMelijnTracks(guildId: ULong): List<Track> {
        val settings by inject<Settings>()
        val sortableTracks = mutableListOf<Pair<BotRestartTrackEntryData, Track>>()
        scopedTransaction {
            BotRestartTrackEntry.join(me.melijn.bot.database.model.Track, JoinType.INNER) {
                me.melijn.bot.database.model.Track.trackType.eq(TrackType.FETCHED)
                    .and(BotRestartTrackEntry.trackId.eq(me.melijn.bot.database.model.Track.trackId))
                    .and(BotRestartTrackEntry.guildId.eq(guildId))
            }.join(me.melijn.bot.database.model.FetchedTrack, JoinType.INNER) {
                me.melijn.bot.database.model.Track.trackId.eq(me.melijn.bot.database.model.FetchedTrack.trackId)
            }.selectAll().forEach {
                val entryData = BotRestartTrackEntryData.fromResRow(it)

                sortableTracks.add(
                    entryData to FetchedTrack(
                        it[me.melijn.bot.database.model.FetchedTrack.trackBase64],
                        it[me.melijn.bot.database.model.Track.title],
                        it[me.melijn.bot.database.model.FetchedTrack.author],
                        it[me.melijn.bot.database.model.Track.url],
                        it[me.melijn.bot.database.model.FetchedTrack.identifier],
                        it[me.melijn.bot.database.model.Track.isStream],
                        me.melijn.bot.music.TrackData.fromNow(
                            PartialUser(
                                Snowflake(settings.bot.id),
                                Settings.bot.username,
                                Settings.bot.discriminator,
                                null
                            ),
                            it[me.melijn.bot.database.model.FetchedTrack.identifier]
                        ),
                        it[me.melijn.bot.database.model.Track.length],
                        it[me.melijn.bot.database.model.FetchedTrack.trackSource],
                        it[me.melijn.bot.database.model.FetchedTrack.trackInfoVersion].toByte(),
                    )
                )
            }
            BotRestartTrackEntry.join(me.melijn.bot.database.model.Track, JoinType.INNER) {
                me.melijn.bot.database.model.Track.trackType.eq(TrackType.SPOTIFY)
                    .and(BotRestartTrackEntry.trackId.eq(me.melijn.bot.database.model.Track.trackId))
                    .and(BotRestartTrackEntry.guildId.eq(guildId))
            }.join(me.melijn.bot.database.model.SpotifyTrack, JoinType.INNER) {
                me.melijn.bot.database.model.Track.trackId.eq(me.melijn.bot.database.model.SpotifyTrack.trackId)
            }.selectAll().forEach {
                val entryData = BotRestartTrackEntryData.fromResRow(it)
                sortableTracks.add(
                    entryData to SpotifyTrack(
                            it[me.melijn.bot.database.model.Track.title],
                            it[me.melijn.bot.database.model.SpotifyTrack.author],
                            it[me.melijn.bot.database.model.Track.url],
                            it[me.melijn.bot.database.model.SpotifyTrack.identifier],
                            it[me.melijn.bot.database.model.Track.isStream],
                            me.melijn.bot.music.TrackData.fromNow(
                                PartialUser(
                                    Snowflake(settings.bot.id),
                                    Settings.bot.username,
                                    Settings.bot.discriminator,
                                    null
                                ),
                                it[me.melijn.bot.database.model.SpotifyTrack.identifier]
                            ),
                            it[me.melijn.bot.database.model.Track.length]
                        )
                    )
            }
        }
        return sortableTracks.sortedBy { it.first.position }.map { it.second }
    }
}

@Inject
class BotRestartTrackQueueManager(driverManager: DriverManager) : AbstractBotRestartTrackQueueManager(driverManager) {

    fun getAll(): List<BotRestartTrackQueueData> {
        return scopedTransaction {
            BotRestartTrackQueue.select {
                PodCheckOp(BotRestartTrackQueue.guildId)
            }.map {
                BotRestartTrackQueueData.fromResRow(it)
            }
        }
    }

}

class PodCheckOp(
    val guildIdColumn: ExpressionWithColumnType<ULong>
) : Op<Boolean>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("((")
        guildIdColumn.toQueryBuilder(queryBuilder)
        queryBuilder.append(" >> 22) % ${PodInfo.shardCount}) IN (${PodInfo.shardList.joinToString(", ")})")
    }
}
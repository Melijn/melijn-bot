package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import kotlinx.datetime.toLocalDateTime
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.BotRestartTrackEntry
import me.melijn.bot.database.model.BotRestartTrackQueue
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.model.PartialUser
import me.melijn.bot.model.PodInfo
import me.melijn.bot.music.Track
import me.melijn.bot.music.TrackType
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.gen.BotRestartTrackEntryData
import me.melijn.gen.BotRestartTrackQueueData
import me.melijn.gen.Settings
import me.melijn.gen.database.manager.AbstractBotRestartTrackEntryManager
import me.melijn.gen.database.manager.AbstractBotRestartTrackQueueManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.utils.TimeUtil
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import me.melijn.bot.database.model.Track as DBTrack

@Inject
class BotRestartTrackEntryManager(driverManager: DriverManager) : AbstractBotRestartTrackEntryManager(driverManager) {

    private val trackManager by inject<TrackManager>()
    private val settings by inject<Settings>()

    fun newTrack(guildId: ULong, position: Int, track: Track) {
        val trackId = trackManager.storeMusicTrack(track)

        val userId = track.data?.requester?.idULong ?: settings.bot.id.toULong()
        val utc = kotlinx.datetime.TimeZone.UTC
        val addedAt = track.data?.requestedAt?.toLocalDateTime(utc) ?: TimeUtil.localDateTimeNow()

        store(BotRestartTrackEntryData(guildId, trackId, position, userId, addedAt))
    }

    fun getMelijnTracks(guildId: ULong): List<Track> {
        val sortableTracks = mutableListOf<Pair<BotRestartTrackEntryData, Track>>()
        val where = { trackType: TrackType ->
            DBTrack.trackType.eq(trackType)
                .and(BotRestartTrackEntry.trackId.eq(DBTrack.trackId))
                .and(BotRestartTrackEntry.guildId.eq(guildId))
        }
        val trackCollector = { it: ResultRow, trackType: TrackType ->
            val entryData = BotRestartTrackEntryData.fromResRow(it)
            val requester = PartialUser(
                Snowflake(settings.bot.id),
                Settings.bot.username,
                Settings.bot.discriminator,
                null
            )
            val track = when (trackType) {
                TrackType.SPOTIFY -> trackManager.spotifyTrackFromResRow(it, requester)
                TrackType.FETCHED -> trackManager.fetchedTrackFromResRow(it, requester)
            }
            sortableTracks.add(entryData to track)
        }
        trackManager.joinAllTypesInto(PlaylistTrack, where, trackCollector)
        return sortableTracks.sortedBy { it.first.position }.map { it.second }
    }

    fun deleteAll(shardId: Int) {
        scopedTransaction {
            BotRestartTrackEntry.deleteWhere {
                PodCheckOp(BotRestartTrackEntry.guildId, shardId)
            }
        }
    }
}

@Inject
class BotRestartTrackQueueManager(driverManager: DriverManager) : AbstractBotRestartTrackQueueManager(driverManager) {

    fun getAll(shardId: Int): List<BotRestartTrackQueueData> {
        return scopedTransaction {
            BotRestartTrackQueue.select {
                PodCheckOp(BotRestartTrackQueue.guildId, shardId)
            }.map {
                BotRestartTrackQueueData.fromResRow(it)
            }
        }
    }

    fun deleteAll(shardId: Int) {
        scopedTransaction {
            BotRestartTrackQueue.deleteWhere {
                PodCheckOp(BotRestartTrackQueue.guildId, shardId)
            }
        }
    }
}

class PodCheckOp(
    private val guildIdColumn: ExpressionWithColumnType<ULong>,
    private val shardId: Int
) : Op<Boolean>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("((")
        guildIdColumn.toQueryBuilder(queryBuilder)
        queryBuilder.append(" >> 22) % ${PodInfo.shardCount}) = $shardId")
    }
}
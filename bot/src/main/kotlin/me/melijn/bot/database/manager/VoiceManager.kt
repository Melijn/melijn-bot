package me.melijn.bot.database.manager

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.LeaderboardData
import me.melijn.bot.database.model.MissingMembers
import me.melijn.bot.utils.ExposedUtil.CustomExpression
import me.melijn.bot.utils.ExposedUtil.intervalEpoch
import me.melijn.bot.utils.ExposedUtil.retype
import me.melijn.bot.utils.TimeUtil.formatElapsedVerbose
import me.melijn.gen.database.manager.AbstractVoiceSessionsManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.logger.logger
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinDurationColumnType
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import me.melijn.bot.database.model.VoiceSessions as VS

@Inject
class VoiceManager(override val driverManager: DriverManager) : AbstractVoiceSessionsManager(driverManager) {

    /**
     * Table of users in voice chat right now.
     * Used to calculate VC times, and as "pending time" if the VC commands are used while the user is in VC
     */
    private val joinTimes = ConcurrentHashMap<Long, Instant>()
    private val logger = logger()

    fun putJoinTime(userId: Long, joinTime: Instant) {
        if (joinTimes.put(userId, joinTime) != null)
            logger.warn("$userId joined a voice channel twice without leaving")
    }

    suspend fun insertJoinNow(guild: Long, channel: Long, member: Long) {
        val now = Clock.System.now()
        putJoinTime(member, now)

        scopedTransaction {
            VS.insert {
                it[this.guildId] = guild
                it[this.channelId] = channel
                it[this.userId] = member
                it[this.joined] = now
            }
        }
    }

    /**
     * Insert a leave event, returning how long the user spent in VC
     *
     * @return `null` if no corresponding join event was found, and thus the duration spent is unknown
     */
    suspend fun insertLeaveNow(member: Long): Duration? {
        val now = Clock.System.now()

        return joinTimes.remove(member)?.let { joinTime ->
            scopedTransaction {
                VS.update({
                    (VS.userId eq member) and (VS.joined eq joinTime)
                }) {
                    it[this.left] = now
                }
            }

            now - joinTime
        }
    }

    suspend fun getPersonalVoiceStatistics(guild: Long, member: Long): Duration {
        val timeSpent = scopedTransaction {
            val sum = intervalEpoch(Sum(retype<Duration?>(VS.left minus VS.joined), KotlinDurationColumnType()))
                .alias("sum")

            VS.slice(sum)
                .select((VS.userId eq member) and (VS.guildId eq guild))
                .firstOrNull()
                ?.get(sum)
        } ?: Duration.ZERO

        return timeSpent + (getTimeInVCRightNow(member) ?: Duration.ZERO)
    }

    suspend fun getGuildStatistics(guild: Long, count: Int, offset: Long) = scopedTransaction {
        val timeSpent = retype<Duration?>(VS.left minus VS.joined)
        val sum = intervalEpoch(Sum(timeSpent, KotlinDurationColumnType())).alias("sum")
        val max = intervalEpoch(Max(timeSpent, KotlinDurationColumnType())).alias("max")

        VS.join(MissingMembers, JoinType.LEFT, additionalConstraint = {
            VS.guildId.eq(MissingMembers.guildId).and(VS.userId.eq(MissingMembers.userId))
        }).slice(VS.userId, sum, max)
            .select(VS.guildId eq guild)
            .orderBy(sum to SortOrder.DESC)
            .groupBy(VS.userId)
            .limit(count, offset)
            .map { row ->
                val userId = row[VS.userId]
                val timeNow = getTimeInVCRightNow(userId) ?: Duration.ZERO
                val missing = row.getOrNull(MissingMembers.userId) != null
                GuildStatisticsEntry(
                    userId,
                    count + offset,
                    row[sum]?.plus(timeNow) ?: Duration.ZERO,
                    maxOf(row[max] ?: Duration.ZERO, timeNow),
                    missing
                )
            }
            .sortedByDescending { it.timeSpentTotal }
    }

    /**
     * Get voice entries that don't have a leave entry and aren't older than a day
     */
    suspend fun getDanglingJoins() = scopedTransaction {
        val max = VS.joined.max()
        val maxAlias = max.alias("max")
        val maxes = VS
            .slice(VS.userId, maxAlias)
            .selectAll()
            .groupBy(VS.userId)
            .alias("maxes")

        maxes
            .join(VS, JoinType.INNER, VS.userId, maxes[VS.userId]) {
                (VS.joined eq maxes[maxAlias]) and
                        (VS.joined greater CustomExpression("now() - INTERVAL '1 DAY'"))
            }
            .slice(VS.guildId, VS.channelId, VS.userId, VS.joined)
            .select(VS.left.isNull())
            .map { row ->
                VoiceJoinEntry(
                    row[VS.guildId],
                    row[VS.channelId],
                    row[VS.userId],
                    row[VS.joined]
                )
            }
    }

    private fun getTimeInVCRightNow(member: Long) =
        this.joinTimes[member]?.let { Clock.System.now() - it }

    suspend fun rowCount(guildId: Long): Long = scopedTransaction {
        VS.slice(VS.userId.countDistinct()).select { VS.guildId.eq(guildId) }.groupBy(VS.guildId).first().let {
            it[VS.userId.countDistinct()]
        }
    }

    suspend fun getPosition(guildId: Long, invokerId: Long): LeaderboardData? = suspendCoroutine { continuation ->
        @Language("postgresql")
        val query1 =
            """SELECT subq.guild_id, subq.user_id, subq.total_time, subq.longest_time, position, subq.muser_id
FROM (
         SELECT voice_sessions.guild_id, voice_sessions.user_id, sum(voice_sessions.left - voice_sessions.joined) as total_time, max(voice_sessions.left - voice_sessions.joined) as longest_time, ROW_NUMBER() over (order by sum(voice_sessions.left - voice_sessions.joined) desc) as position,
                missing_members.user_id as muser_id
         FROM voice_sessions LEFT JOIN missing_members ON voice_sessions.guild_id = missing_members.guild_id AND voice_sessions.user_id = missing_members.user_id
         WHERE (voice_sessions.guild_id = ?)
         GROUP BY voice_sessions.guild_id, voice_sessions.user_id, missing_members.user_id
         ORDER BY total_time DESC) subq
WHERE subq.user_id = ?
                    """
        driverManager.executeQuery(query1, { rs ->
            if (rs.next()) {
                // https://stackoverflow.com/questions/2920364/checking-for-a-null-int-value-from-a-java-resultset
                // Makes rs work on missing_members.user_id and consider it's nullability below
                rs.getLong(6)
                val missing = !rs.wasNull()

                val entry = GuildStatisticsEntry(
                    rs.getLong(2),
                    rs.getLong(5),
                    rs.getTimestamp(3).nanos.nanoseconds,
                    rs.getTimestamp(4).nanos.nanoseconds,
                    missing
                )
                continuation.resume(entry)
            } else continuation.resume(null)
        }, guildId, invokerId)
    }

    data class GuildStatisticsEntry(
        override val userId: Long,
        override val position: Long,
        val timeSpentTotal: Duration,
        val timeSpentLongest: Duration,
        override val missing: Boolean
    ) : LeaderboardData {

        override val dataList: List<String> = listOf(timeSpentTotal, timeSpentLongest).map { it.formatElapsedVerbose() }
    }

    data class VoiceJoinEntry(val guild: Long, val channel: Long, val userId: Long, val timestamp: Instant)

}
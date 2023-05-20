package me.melijn.bot.database.manager

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.ExposedUtil.CustomExpression
import me.melijn.bot.utils.ExposedUtil.intervalEpoch
import me.melijn.bot.utils.ExposedUtil.retype
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.logger.logger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinDurationColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import me.melijn.bot.database.model.VoiceSessions as VS

@Inject
class VoiceManager(val driverManager: DriverManager) {

    /**
     * Table of users in voice chat right now.
     * Used to calculate VC times, and as "pending time" if the VC commmands are used while the user is in VC
     */
    private val joinTimes = ConcurrentHashMap<Long, Instant>()
    private val logger = logger()

    fun putJoinTime(userId: Long, joinTime: Instant) {
        if (joinTimes.put(userId, joinTime) != null)
            logger.warn("$userId joined a voice channel twice without leaving")
    }

    fun insertJoinNow(guild: Long, channel: Long, member: Long) {
        val now = Clock.System.now()
        putJoinTime(member, now)

        transaction(driverManager.database) {
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
    fun insertLeaveNow(member: Long): Duration? {
        val now = Clock.System.now()

        return joinTimes.remove(member)?.let { joinTime ->
            transaction {
                VS.update({
                    (VS.userId eq member) and (VS.joined eq joinTime)
                }) {
                    it[this.left] = now
                }
            }

            now - joinTime
        }
    }

    fun getPersonalVoiceStatistics(guild: Long, member: Long): Duration {
        val timeSpent = transaction(driverManager.database) {
            val sum = intervalEpoch(Sum(retype<Duration?>(VS.left minus VS.joined), KotlinDurationColumnType()))
                .alias("sum")

            VS.slice(sum)
                .select((VS.userId eq member) and (VS.guildId eq guild))
                .firstOrNull()
                ?.get(sum)
        } ?: Duration.ZERO

        return timeSpent + (getTimeInVCRightNow(member) ?: Duration.ZERO)
    }

    fun getGuildStatistics(guild: Long) = transaction(driverManager.database) {
        val timeSpent = retype<Duration?>(VS.left minus VS.joined)
        val sum = intervalEpoch(Sum(timeSpent, KotlinDurationColumnType())).alias("sum")
        val max = intervalEpoch(Max(timeSpent, KotlinDurationColumnType())).alias("max")

        VS.slice(VS.userId, sum, max)
            .select(VS.guildId eq guild)
            .orderBy(sum to SortOrder.DESC)
            .groupBy(VS.userId)
            .limit(9)
            .map { row ->
                val userId = row[VS.userId]
                val timeNow = getTimeInVCRightNow(userId) ?: Duration.ZERO
                GuildStatisticsEntry(
                    userId,
                    row[sum]?.plus(timeNow) ?: Duration.ZERO,
                    maxOf(row[max] ?: Duration.ZERO, timeNow)
                )
            }
            .sortedByDescending { it.timeSpentTotal }
    }

    /**
     * Get voice entries that don't have a leave entry and aren't older than a day
     */
    fun getDanglingJoins() = transaction(driverManager.database) {
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

    data class GuildStatisticsEntry(val userId: Long, val timeSpentTotal: Duration, val timeSpentLongest: Duration)

    data class VoiceJoinEntry(val guild: Long, val channel: Long, val userId: Long, val timestamp: Instant)

}
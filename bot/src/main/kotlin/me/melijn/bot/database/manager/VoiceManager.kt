package me.melijn.bot.database.manager

import kotlinx.datetime.Clock
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.VoiceJoins
import me.melijn.bot.database.model.VoiceLeaves
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration

@Inject
class VoiceManager(val driverManager: DriverManager) {

    fun insertJoinNow(guild: Long, channel: Long, member: Long) {
        transaction(driverManager.database) {
            VoiceJoins.insert {
                it[this.guildId] = guild
                it[this.channelId] = channel
                it[this.userId] = member
                it[this.timestamp] = Clock.System.now()
            }
        }
    }

    fun insertLeaveNow(guild: Long, channel: Long, member: Long, timeSpent: Duration?) {
        transaction(driverManager.database) {
            VoiceLeaves.insert {
                it[this.guildId] = guild
                it[this.channelId] = channel
                it[this.userId] = member
                it[this.timestamp] = Clock.System.now()
                it[this.timeSpent] = timeSpent
            }
        }
    }

    fun getPersonalVoiceStatistics(guild: Long, member: Long): Duration? {
        val duration = transaction(driverManager.database) {
            VoiceLeaves.slice(VoiceLeaves.timeSpent.sum())
                .select {
                    (VoiceLeaves.userId eq member) and (VoiceLeaves.guildId eq guild)
                }
                .firstOrNull()
                ?.get(VoiceLeaves.timeSpent.sum())
        }

        return duration
    }

    fun getGuildStatistics(guild: Long) = transaction(driverManager.database) {
        VoiceLeaves.slice(VoiceLeaves.userId, VoiceLeaves.timeSpent.sum())
            .select {
                (VoiceLeaves.guildId eq guild) and (VoiceLeaves.timeSpent.isNotNull())
            }
            .orderBy(VoiceLeaves.timeSpent.sum() to SortOrder.DESC)
            .groupBy(VoiceLeaves.userId)
            .limit(10)
            .associate { row ->
                row[VoiceLeaves.userId] to row[VoiceLeaves.timeSpent.sum()]
            }
    }

}
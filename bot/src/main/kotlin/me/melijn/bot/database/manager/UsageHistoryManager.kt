package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.UseLimitHitType
import me.melijn.bot.database.model.UserCommandUseLimitHistory
import me.melijn.bot.database.model.UserUseLimitHistory
import me.melijn.bot.model.kordex.MelUsageHistory
import me.melijn.gen.UsageHistoryData
import me.melijn.gen.database.manager.AbstractUsageHistoryManager
import me.melijn.gen.database.manager.AbstractUserCommandUseLimitHistoryManager
import me.melijn.gen.database.manager.AbstractUserUseLimitHistoryManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import me.melijn.bot.database.model.UsageHistory as DBUsageHistory

inline val Int.b get() = this.toByte()

@Inject
class DBUsageHistoryManager(override val driverManager: DriverManager) :
    AbstractUsageHistoryManager(driverManager)

@Inject
class UserCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractUserCommandUseLimitHistoryManager(driverManager)

@Inject
class UserUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractUserUseLimitHistoryManager(driverManager)

@Inject
class UsageHistoryManager(
    private val usageHistoryManager: DBUsageHistoryManager,
    private val userCommandUseLimitHistoryManager: UserCommandUseLimitHistoryManager,
    private val userUseLimitHistoryManager: UserUseLimitHistoryManager,
) {

    /** utils **/
    private fun intoUsageHistory(
        entries: List<UsageHistoryData>,
        limitHitEntries: Map<UseLimitHitType, List<Long>>
    ): MelUsageHistory {
        val usageHistory = entries.map { it.moment.toEpochMilliseconds() }

        return MelUsageHistory(
            limitHitEntries[UseLimitHitType.COOLDOWN] ?: emptyList(),
            limitHitEntries[UseLimitHitType.RATELIMIT] ?: emptyList(),
            false,
            usageHistory,
        )
    }

    private fun runQueriesForHitTypes(
        usageHistory: MelUsageHistory,
        deleteFunc: (Instant, UseLimitHitType) -> Int,
        insertFunc: (List<Long>, UseLimitHitType) -> List<ResultRow>,
    ) {
        usageHistoryManager.scopedTransaction {
            val changes = usageHistory.changes
            for (type in UseLimitHitType.values()) {
                val (added, limit) = when (type) {
                    UseLimitHitType.COOLDOWN -> changes.crossedCooldownsChanges
                    UseLimitHitType.RATELIMIT -> changes.crossedLimitChanges
                }
                if (limit != null) {
                    val moment = Instant.fromEpochMilliseconds(limit)

                    // Deletes expired entries for this scope
                    deleteFunc(moment, type)
                }
                insertFunc(added, type)
            }
        }
    }

    /** (userId, commandId) use limit history scope **/
    fun getUserCmdHistory(userId: ULong, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByUserCommandKey(userId, commandId)
        val limitHitEntries = userCommandUseLimitHistoryManager.getByUserCommandKey(userId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setUserCmdHistSerialized(userId: ULong, commandId: Int, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, { moment, type ->
            UserCommandUseLimitHistory.deleteWhere {
                (UserCommandUseLimitHistory.moment less moment) and
                        (UserCommandUseLimitHistory.type eq type) and
                        (UserCommandUseLimitHistory.userId eq userId) and
                        (UserCommandUseLimitHistory.commandId eq commandId)
            }
        }, { added, type ->
            UserCommandUseLimitHistory.batchInsert(added, shouldReturnGeneratedValues = false, ignore = true) {
                this[UserCommandUseLimitHistory.userId] = userId
                this[UserCommandUseLimitHistory.commandId] = commandId
                this[UserCommandUseLimitHistory.type] = type
                this[UserCommandUseLimitHistory.moment] = Instant.fromEpochMilliseconds(it)
            }
        })

    /** (userId) user limit history scope **/
    fun getUserHistory(userId: ULong): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByUserKey(userId)
        val limitHitEntries = userUseLimitHistoryManager.getByUserKey(userId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setUserHistSerialized(userId: ULong, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, { moment, type ->
            UserUseLimitHistory.deleteWhere {
                (UserUseLimitHistory.moment less moment) and
                        (UserUseLimitHistory.type eq type) and
                        (UserUseLimitHistory.userId eq userId)
            }
        }, { added, type ->
            UserUseLimitHistory.batchInsert(added, shouldReturnGeneratedValues = false, ignore = true) {
                this[UserUseLimitHistory.userId] = userId
                this[UserUseLimitHistory.type] = type
                this[UserUseLimitHistory.moment] = Instant.fromEpochMilliseconds(it)
            }
        })

    fun updateUsage(guildId: Snowflake?, channelId: Snowflake, userId: Snowflake, commandId: Int) {
        val moment = Clock.System.now()
        usageHistoryManager.scopedTransaction {
            usageHistoryManager.store(
                UsageHistoryData(
                    guildId?.value,
                    channelId.value,
                    userId.value,
                    commandId,
                    moment
                )
            )

            DBUsageHistory.deleteWhere {
                (DBUsageHistory.userId eq userId.value) and (DBUsageHistory.moment less moment)
            }
        }
    }
}
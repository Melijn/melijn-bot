package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.*
import me.melijn.bot.model.kordex.MelUsageHistory
import me.melijn.gen.UsageHistoryData
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import me.melijn.bot.database.model.UsageHistory as DBUsageHistory

inline val Int.b get() = this.toByte()

@Inject
class DBUsageHistoryManager(override val driverManager: DriverManager) :
    AbstractUsageHistoryManager(driverManager)

@Inject // User command
class UserCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractUserCommandUseLimitHistoryManager(driverManager)

@Inject // User
class UserUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractUserUseLimitHistoryManager(driverManager)

@Inject
class GuildUserUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractGuildUserUseLimitHistoryManager(driverManager)

@Inject // Channel
class ChannelUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractChannelUseLimitHistoryManager(driverManager)

@Inject // Guild
class GuildUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractGuildUseLimitHistoryManager(driverManager)

@Inject // Guild user command
class GuildUserCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractGuildUserCommandUseLimitHistoryManager(driverManager)

@Inject // Channel command
class ChannelCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractChannelCommandUseLimitHistoryManager(driverManager)

@Inject // Channel user command
class ChannelUserCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractChannelUserCommandUseLimitHistoryManager(driverManager)

@Inject // Channel user
class ChannelUserUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractChannelUserCommandUseLimitHistoryManager(driverManager)

@Inject // Guild command
class GuildCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractGuildCommandUseLimitHistoryManager(driverManager)

class UsageHistoryManager(
    private val usageHistoryManager: DBUsageHistoryManager,
    private val guildUserUseLimitHistoryManager: GuildUserUseLimitHistoryManager,
    private val userCommandUseLimitHistoryManager: UserCommandUseLimitHistoryManager,
    private val userUseLimitHistoryManager: UserUseLimitHistoryManager,
    private val guildUseLimitHistoryManager: GuildUseLimitHistoryManager,
    private val channelUseLimitHistoryManager: ChannelUseLimitHistoryManager,
    private val guildUserCommandUseLimitHistory: GuildUserCommandUseLimitHistoryManager,
    private val channelCommandUseLimitHistory: ChannelCommandUseLimitHistoryManager,
    private val channelUserCommandUseLimitHistory: ChannelUserCommandUseLimitHistoryManager,
    private val channelUserUseLimitHistory: ChannelUserUseLimitHistoryManager,
    private val guildCommandUseLimitHistory: GuildCommandUseLimitHistoryManager,
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

    private fun <T : Table> runQueriesForHitTypes(
        usageHistory: MelUsageHistory,
        table: T,
        deleteFunc: T.(moment: Instant, type: UseLimitHitType) -> Op<Boolean>,
        insertFunc: BatchInsertStatement.(moment: Instant, type: UseLimitHitType) -> Unit,
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
                    table.deleteWhere {
                        deleteFunc(moment, type)
                    }
                }
                table.batchInsert(added, shouldReturnGeneratedValues = false, ignore = true) {
                    insertFunc(Instant.fromEpochMilliseconds(it), type)
                }
            }
        }
    }

    /** Usage history tracker **/
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

    /** (userId, commandId) use limit history scope **/
    fun getUserCmdHistory(userId: ULong, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByUserCommandKey(userId, commandId)
        val limitHitEntries = userCommandUseLimitHistoryManager.getByUserCommandKey(userId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setUserCmdHistSerialized(userId: ULong, commandId: Int, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, UserCommandUseLimitHistory, { moment, type ->
            (UserCommandUseLimitHistory.moment less moment) and
                    (UserCommandUseLimitHistory.type eq type) and
                    (UserCommandUseLimitHistory.userId eq userId) and
                    (UserCommandUseLimitHistory.commandId eq commandId)
        }, { moment, type ->
            this[UserCommandUseLimitHistory.userId] = userId
            this[UserCommandUseLimitHistory.commandId] = commandId
            this[UserCommandUseLimitHistory.type] = type
            this[UserCommandUseLimitHistory.moment] = moment
        })

    /** (userId) use limit history scope **/
    fun getUserHistory(userId: ULong): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByUserKey(userId)
        val limitHitEntries = userUseLimitHistoryManager.getByUserKey(userId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setUserHistory(userId: ULong, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, UserUseLimitHistory, { moment, type ->
            (UserUseLimitHistory.moment less moment) and
                    (UserUseLimitHistory.type eq type) and
                    (UserUseLimitHistory.userId eq userId)
        }, { moment, type ->
            this[UserUseLimitHistory.userId] = userId
            this[UserUseLimitHistory.type] = type
            this[UserUseLimitHistory.moment] = moment
        })

    /** (guildId, userId) use limit history scope **/
    fun getGuildUserHistory(guildId: ULong, userId: ULong): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildUserKey(guildId, userId)
        val limitHitEntries = guildUserUseLimitHistoryManager.getByGuildUserKey(guildId, userId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildUserHistory(guildId: ULong, userId: ULong, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, GuildUserUseLimitHistory, { moment, type ->
            (GuildUserUseLimitHistory.moment less moment) and
                    (GuildUserUseLimitHistory.type eq type) and
                    (GuildUserUseLimitHistory.userId eq userId) and
                    (GuildUserUseLimitHistory.guildId eq guildId)
        }, { moment, type ->
            this[GuildUserUseLimitHistory.guildId] = guildId
            this[GuildUserUseLimitHistory.userId] = userId
            this[GuildUserUseLimitHistory.type] = type
            this[GuildUserUseLimitHistory.moment] = moment
        })

    /** (channelId) use limit history scope **/
    fun getChannelHistory(channelId: ULong): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelKey(channelId)
        val limitHitEntries = channelUseLimitHistoryManager.getByChannelKey(channelId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelHistory(channelId: ULong, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, ChannelUseLimitHistory, { moment, type ->
            (ChannelUseLimitHistory.moment less moment) and
                    (ChannelUseLimitHistory.type eq type) and
                    (ChannelUseLimitHistory.channelId eq channelId)
        }, { moment, type ->
            this[ChannelUseLimitHistory.channelId] = channelId
            this[ChannelUseLimitHistory.type] = type
            this[ChannelUseLimitHistory.moment] = moment
        })

    /** (guildId) use limit history scope **/
    fun getGuildHistory(guildId: ULong): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildKey(guildId)
        val limitHitEntries = guildUseLimitHistoryManager.getByGuildKey(guildId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildHistory(guildId: ULong, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, GuildUseLimitHistory, { moment, type ->
            (GuildUseLimitHistory.moment less moment) and
                    (GuildUseLimitHistory.type eq type) and
                    (GuildUseLimitHistory.guildId eq guildId)
        }, { moment, type ->
            this[GuildUseLimitHistory.guildId] = guildId
            this[GuildUseLimitHistory.type] = type
            this[GuildUseLimitHistory.moment] = moment
        })

    /** (guildId, userId, commandId) use limit history scope **/
    fun getGuildUserCommandUsageHistory(guildId: ULong, userId: ULong, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildUserCommandKey(guildId, userId, commandId)
        val limitHitEntries = guildUserCommandUseLimitHistory.getByGuildUserCommandKey(guildId, userId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildUserCommandUsageHistory(guildId: ULong, userId: ULong, commandId: Int, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, GuildUserCommandUseLimitHistory, { moment, type ->
            (GuildUserCommandUseLimitHistory.moment less moment) and
                    (GuildUserCommandUseLimitHistory.type eq type) and
                    (GuildUserCommandUseLimitHistory.guildId eq guildId) and
                    (GuildUserCommandUseLimitHistory.userId eq userId) and
                    (GuildUserCommandUseLimitHistory.commandId eq commandId)
        }, { moment, type ->
            this[GuildUserCommandUseLimitHistory.guildId] = guildId
            this[GuildUserCommandUseLimitHistory.userId] = userId
            this[GuildUserCommandUseLimitHistory.commandId] = commandId
            this[GuildUserCommandUseLimitHistory.type] = type
            this[GuildUserCommandUseLimitHistory.moment] = moment
        })

    /** (channelId, commandId) use limit history scope **/
    fun getChannelCommandHistory(channelId: ULong, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelKey(channelId)
        val limitHitEntries = channelCommandUseLimitHistory.getByChannelCommandKey(channelId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelCommandHistory(channelId: ULong, commandId: Int, usageHistory: MelUsageHistory) =
        runQueriesForHitTypes(usageHistory, ChannelCommandUseLimitHistory, { moment, type ->
            (ChannelCommandUseLimitHistory.moment less moment) and
                    (ChannelCommandUseLimitHistory.type eq type) and
                    (ChannelCommandUseLimitHistory.channelId eq channelId) and
                    (ChannelCommandUseLimitHistory.commandId eq commandId)
        }, { moment, type ->
            this[ChannelCommandUseLimitHistory.channelId] = channelId
            this[ChannelCommandUseLimitHistory.commandId] = commandId
            this[ChannelCommandUseLimitHistory.type] = type
            this[ChannelCommandUseLimitHistory.moment] = moment
        })

    /** (channelId, userId, commandId) use limit history **/
    fun getChannelUserCommandUsageHistory(channelId: ULong, userId: ULong, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelUserCommandKey(channelId, userId, commandId)
        val limitHitEntries = channelUserCommandUseLimitHistory.getByChannelUserCommandKey(channelId
               , userId, commandId)
           .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelUserCommandUsageHistory(channelId: ULong, userId: ULong, commandId: Int, usageHistory: MelUsageHistory) {
        runQueriesForHitTypes(usageHistory, ChannelUserCommandUseLimitHistory, { moment, type   ->
            (ChannelUserCommandUseLimitHistory.moment less moment) and
                    (ChannelUserCommandUseLimitHistory.type eq type) and
                    (ChannelUserCommandUseLimitHistory.channelId eq channelId) and
                    (ChannelUserCommandUseLimitHistory.userId eq userId) and
                    (ChannelUserCommandUseLimitHistory.commandId eq commandId)
            }, { moment, type   ->
                this[ChannelUserCommandUseLimitHistory.channelId] = channelId
                this[ChannelUserCommandUseLimitHistory.userId] = userId
                this[ChannelUserCommandUseLimitHistory.commandId] = commandId
                this[ChannelUserCommandUseLimitHistory.type] = type
                this[ChannelUserCommandUseLimitHistory.moment] = moment
        })
    }

    /** (guildId, commandId) use limit history **/
    fun getGuildCommandUsageHistory(guildId: ULong, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildCommandKey(guildId, commandId)
        val limitHitEntries = guildCommandUseLimitHistory.getByGuildCommandKey(guildId, commandId)
           .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildCommandUsageHistory(guildId: ULong, commandId: Int, usageHistory: MelUsageHistory) {
        runQueriesForHitTypes(usageHistory, GuildCommandUseLimitHistory, { moment, type ->
            (GuildCommandUseLimitHistory.moment less moment) and
                    (GuildCommandUseLimitHistory.type eq type) and
                    (GuildCommandUseLimitHistory.guildId eq guildId) and
                    (GuildCommandUseLimitHistory.commandId eq commandId)
            }, { moment, type ->
                this[GuildCommandUseLimitHistory.guildId] = guildId
                this[GuildCommandUseLimitHistory.commandId] = commandId
                this[GuildCommandUseLimitHistory.type] = type
                this[GuildCommandUseLimitHistory.moment] = moment
        })
    }
}
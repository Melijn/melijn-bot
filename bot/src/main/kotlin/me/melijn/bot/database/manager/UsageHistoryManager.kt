package me.melijn.bot.database.manager

import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.*
import me.melijn.bot.model.kordex.MelUsageHistory
import me.melijn.gen.UsageHistoryData
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DBTableManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.statements.BatchInsertStatement

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
    AbstractChannelUserUseLimitHistoryManager(driverManager)

@Inject // Guild command
class GuildCommandUseLimitHistoryManager(override val driverManager: DriverManager) :
    AbstractGuildCommandUseLimitHistoryManager(driverManager)

@Inject
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

    /** (userId, commandId) use limit history scope **/
    fun getUserCmdHistory(userId: Long, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByUserCommandKey(userId, commandId)
        val limitHitEntries = userCommandUseLimitHistoryManager.getByUserCommandKey(userId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setUserCmdHistSerialized(userId: Long, commandId: Int, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, UserCommandUseLimitHistory, { moment, type ->
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
    fun getUserHistory(userId: Long): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByUserKey(userId)
        val limitHitEntries = userUseLimitHistoryManager.getByUserKey(userId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setUserHistory(userId: Long, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, UserUseLimitHistory, { moment, type ->
            (UserUseLimitHistory.moment less moment) and
                    (UserUseLimitHistory.type eq type) and
                    (UserUseLimitHistory.userId eq userId)
        }, { moment, type ->
            this[UserUseLimitHistory.userId] = userId
            this[UserUseLimitHistory.type] = type
            this[UserUseLimitHistory.moment] = moment
        })

    /** (guildId, userId) use limit history scope **/
    fun getGuildUserHistory(guildId: Long, userId: Long): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildUserKey(guildId, userId)
        val limitHitEntries = guildUserUseLimitHistoryManager.getByGuildUserKey(guildId, userId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildUserHistory(guildId: Long, userId: Long, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, GuildUserUseLimitHistory, { moment, type ->
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
    fun getChannelHistory(channelId: Long): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelKey(channelId)
        val limitHitEntries = channelUseLimitHistoryManager.getByChannelKey(channelId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelHistory(channelId: Long, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, ChannelUseLimitHistory, { moment, type ->
            (ChannelUseLimitHistory.moment less moment) and
                    (ChannelUseLimitHistory.type eq type) and
                    (ChannelUseLimitHistory.channelId eq channelId)
        }, { moment, type ->
            this[ChannelUseLimitHistory.channelId] = channelId
            this[ChannelUseLimitHistory.type] = type
            this[ChannelUseLimitHistory.moment] = moment
        })

    /** (guildId) use limit history scope **/
    fun getGuildHistory(guildId: Long): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildKey(guildId)
        val limitHitEntries = guildUseLimitHistoryManager.getByGuildKey(guildId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildHistory(guildId: Long, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, GuildUseLimitHistory, { moment, type ->
            (GuildUseLimitHistory.moment less moment) and
                    (GuildUseLimitHistory.type eq type) and
                    (GuildUseLimitHistory.guildId eq guildId)
        }, { moment, type ->
            this[GuildUseLimitHistory.guildId] = guildId
            this[GuildUseLimitHistory.type] = type
            this[GuildUseLimitHistory.moment] = moment
        })

    /** (guildId, userId, commandId) use limit history scope **/
    fun getGuildUserCommandUsageHistory(guildId: Long, userId: Long, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildUserCommandKey(guildId, userId, commandId)
        val limitHitEntries = guildUserCommandUseLimitHistory.getByGuildUserCommandKey(guildId, userId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildUserCommandUsageHistory(guildId: Long, userId: Long, commandId: Int, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, GuildUserCommandUseLimitHistory, { moment, type ->
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
    fun getChannelCommandHistory(channelId: Long, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelKey(channelId)
        val limitHitEntries = channelCommandUseLimitHistory.getByChannelCommandKey(channelId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelCommandHistory(channelId: Long, commandId: Int, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, ChannelCommandUseLimitHistory, { moment, type ->
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
    fun getChannelUserCommandUsageHistory(channelId: Long, userId: Long, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelUserCommandKey(channelId, userId, commandId)
        val limitHitEntries = channelUserCommandUseLimitHistory.getByChannelUserCommandKey(
            channelId, userId, commandId
        )
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelUserCommandUsageHistory(
        channelId: Long,
        userId: Long,
        commandId: Int,
        usageHistory: MelUsageHistory
    ) {
        usageHistoryManager.runQueriesForHitTypes(usageHistory, ChannelUserCommandUseLimitHistory, { moment, type ->
            (ChannelUserCommandUseLimitHistory.moment less moment) and
                    (ChannelUserCommandUseLimitHistory.type eq type) and
                    (ChannelUserCommandUseLimitHistory.channelId eq channelId) and
                    (ChannelUserCommandUseLimitHistory.userId eq userId) and
                    (ChannelUserCommandUseLimitHistory.commandId eq commandId)
        }, { moment, type ->
            this[ChannelUserCommandUseLimitHistory.channelId] = channelId
            this[ChannelUserCommandUseLimitHistory.userId] = userId
            this[ChannelUserCommandUseLimitHistory.commandId] = commandId
            this[ChannelUserCommandUseLimitHistory.type] = type
            this[ChannelUserCommandUseLimitHistory.moment] = moment
        })
    }

    /** (channelId, userId) use limit history **/
    fun getChannelUserUsageHistory(channelId: Long, userId: Long): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByChannelUserKey(channelId, userId)
        val limitHitEntries = channelUserUseLimitHistory.getByChannelUserKey(
            channelId, userId
        )
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setChannelUserUsageHistory(channelId: Long, userId: Long, usageHistory: MelUsageHistory) {
        usageHistoryManager.runQueriesForHitTypes(usageHistory, ChannelUserUseLimitHistory, { moment, type ->
            (ChannelUserUseLimitHistory.moment less moment) and
                    (ChannelUserUseLimitHistory.type eq type) and
                    (ChannelUserUseLimitHistory.channelId eq channelId) and
                    (ChannelUserUseLimitHistory.userId eq userId)
        }, { moment, type ->
            this[ChannelUserUseLimitHistory.channelId] = channelId
            this[ChannelUserUseLimitHistory.userId] = userId
            this[ChannelUserUseLimitHistory.type] = type
            this[ChannelUserUseLimitHistory.moment] = moment
        })
    }

    /** (guildId, commandId) use limit history **/
    fun getGuildCommandUsageHistory(guildId: Long, commandId: Int): MelUsageHistory {
        val usageEntries = usageHistoryManager.getByGuildCommandKey(guildId, commandId)
        val limitHitEntries = guildCommandUseLimitHistory.getByGuildCommandKey(guildId, commandId)
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }

    fun setGuildCommandUsageHistory(guildId: Long, commandId: Int, usageHistory: MelUsageHistory) {
        usageHistoryManager.runQueriesForHitTypes(usageHistory, GuildCommandUseLimitHistory, { moment, type ->
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

/** utils **/
fun intoUsageHistory(
    entries: List<UsageHistoryData>,
    limitHitEntries: Map<UseLimitHitType, List<Long>>
): MelUsageHistory {
    val usageHistory = entries.map { it.moment }

//    limitHitEntries[UseLimitHitType.COOLDOWN] ?: emptyList(),
//    limitHitEntries[UseLimitHitType.RATELIMIT] ?: emptyList(),
//    false,

    return MelUsageHistory(

        usageHistory,
    )
}

fun <T : Table> DBTableManager<*>.runQueriesForHitTypes(
    usageHistory: MelUsageHistory,
    table: T,
    deleteFunc: T.(moment: Instant, type: UseLimitHitType) -> Op<Boolean>,
    insertFunc: BatchInsertStatement.(moment: Instant, type: UseLimitHitType) -> Unit,
) {
    scopedTransaction {
        val changes = usageHistory.changes
        for (type in UseLimitHitType.values()) {
            val (added, limit) = when (type) {
                UseLimitHitType.COOLDOWN -> changes.crossedCooldownsChanges
                UseLimitHitType.RATELIMIT -> changes.crossedLimitChanges
            }
            if (limit != null) {
                val moment = limit

                // Deletes expired entries for this scope
                table.deleteWhere {
                    deleteFunc(moment, type)
                }
            }
            table.batchInsert(added, shouldReturnGeneratedValues = false, ignore = true) {
                insertFunc(it, type)
            }
        }
    }
}
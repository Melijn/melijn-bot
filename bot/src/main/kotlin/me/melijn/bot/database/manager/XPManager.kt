package me.melijn.bot.database.manager

import com.kotlindiscord.kord.extensions.ExtensibleBot
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.LEVEL_LOG_BASE
import me.melijn.bot.commands.LeaderboardData
import me.melijn.bot.commands.LevelingExtension.Companion.getLevel
import me.melijn.bot.database.model.*
import me.melijn.bot.events.leveling.GuildXPChangeEvent
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.GlobalXPData
import me.melijn.gen.GuildXPData
import me.melijn.gen.LevelRolesData
import me.melijn.gen.TopRolesData
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.Member
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration

@Inject
class GlobalXPManager(driverManager: DriverManager) : AbstractGlobalXPManager(driverManager) {

    suspend fun increaseGlobalXP(userSnowflake: ISnowflake, amount: Long) {
        scopedTransaction {
            GlobalXP.insertOrUpdate({
                it[userId] = userSnowflake.idLong
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            })
        }
    }

    suspend fun getTop(count: Int, offset: Long): List<AugmentedGlobalXPData> {
        return scopedTransaction {
            GlobalXP.join(DeletedUsers, JoinType.LEFT) {
                GlobalXP.userId.eq(DeletedUsers.userId)
            }.selectAll()
                .orderBy(GlobalXP.xp, SortOrder.DESC)
                .limit(count, offset)
                .mapIndexed { index, rr ->
                    AugmentedGlobalXPData(
                        GlobalXPData.fromResRow(rr),
                        offset + index,
                        rr.getOrNull(MissingMembers.userId) != null
                    )
                }
        }
    }

    suspend fun rowCount(): Long = scopedTransaction {
        GlobalXP.selectAll().count()
    }

    suspend fun getPosition(userId: Long): AugmentedGlobalXPData? = suspendCoroutine { continuation ->
        @Language("postgresql")
        val query1 =
            """SELECT subq.user_id, subq.xp, position, subq.muser_id
                  |FROM (
                  |    SELECT global_xp.user_id, global_xp.xp, ROW_NUMBER() over (order by global_xp.xp desc) as position, 
                  |        deleted_users.user_id as muser_id
                  |    FROM global_xp LEFT JOIN deleted_users ON global_xp.user_id = deleted_users.user_id
                  |    ORDER BY global_xp.xp DESC) subq 
                  |WHERE subq.user_id = ?
                  |""".trimMargin()
        driverManager.executeQuery(query1, { rs ->
            if (rs.next()) {
                println(rs)

                val entry = GlobalXPData(
                    rs.getLong(1),
                    rs.getLong(2)
                )
                val rowNumber = rs.getLong(3)

                // https://stackoverflow.com/questions/2920364/checking-for-a-null-int-value-from-a-java-resultset
                // Makes rs work on missing_users.user_id and consider it's nullability below
                rs.getLong(4)
                val augmentedGuildXPData = AugmentedGlobalXPData(entry, rowNumber, !rs.wasNull())
                continuation.resume(augmentedGuildXPData)
            } else continuation.resume(null)
        }, userId)
    }
}

data class AugmentedGlobalXPData(
    val globalXPData: GlobalXPData,
    override val position: Long,
    override val missing: Boolean
) : LeaderboardData {
    override val userId = globalXPData.userId
    override val dataList: List<Long> = listOf(getLevel(globalXPData.xp, LEVEL_LOG_BASE), globalXPData.xp)
}

data class AugmentedGuildXPData(
    val guildXPData: GuildXPData,
    override val position: Long,
    override val missing: Boolean
) : LeaderboardData {
    override val userId = guildXPData.userId
    override val dataList: List<Long> = listOf(getLevel(guildXPData.xp, LEVEL_LOG_BASE), guildXPData.xp)
}

@Inject
class GuildXPManager(driverManager: DriverManager) : AbstractGuildXPManager(driverManager) {

    suspend fun increaseGuildXP(guildSnowflake: ISnowflake, userSnowflake: ISnowflake, amount: Long) {
        scopedTransaction {
            GuildXP.insertOrUpdate({
                it[guildId] = guildSnowflake.idLong
                it[userId] = userSnowflake.idLong
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            })
        }
    }

    suspend fun getPosition(guildId: Long, userId: Long): AugmentedGuildXPData? = suspendCoroutine { continuation ->
        @Language("postgresql")
        val query1 =
            """SELECT subq.guild_id, subq.user_id, subq.xp, position, subq.muser_id
                  |FROM (
                  |    SELECT guild_xp.guild_id, guild_xp.user_id, guild_xp.xp, ROW_NUMBER() over (order by guild_xp.xp desc) as position, 
                  |        missing_members.user_id as muser_id
                  |    FROM guild_xp LEFT JOIN missing_members ON guild_xp.guild_id = missing_members.guild_id AND guild_xp.user_id = missing_members.user_id
                  |    WHERE (guild_xp.guild_id = ?)
                  |    ORDER BY guild_xp.xp DESC) subq 
                  |WHERE subq.user_id = ?
                  |""".trimMargin()
        driverManager.executeQuery(query1, { rs ->
            if (rs.next()) {
                println(rs)

                val entry = GuildXPData(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getLong(3)
                )
                val rowNumber = rs.getLong(4)

                // https://stackoverflow.com/questions/2920364/checking-for-a-null-int-value-from-a-java-resultset
                // Makes rs work on missing_members.user_id and consider it's nullability below
                rs.getLong(5)
                val augmentedGuildXPData = AugmentedGuildXPData(entry, rowNumber, !rs.wasNull())
                continuation.resume(augmentedGuildXPData)
            } else continuation.resume(null)
        }, guildId, userId)
    }

    suspend fun getAtPosition(guildId: Long, position: Long): AugmentedGuildXPData? {
        return getTop(guildId, 1, position).firstOrNull()
    }

    suspend fun getTop(guildId: Long, count: Int, offset: Long): List<AugmentedGuildXPData> {
        return scopedTransaction {
            GuildXP.join(MissingMembers, JoinType.LEFT) {
                GuildXP.guildId.eq(MissingMembers.guildId) and GuildXP.userId.eq(MissingMembers.userId)
            }.select {
                GuildXP.guildId.eq(guildId)
            }.orderBy(GuildXP.xp, SortOrder.DESC)
                .limit(count, offset)
                .mapIndexed { index, rr ->
                    AugmentedGuildXPData(
                        GuildXPData.fromResRow(rr),
                        offset + index,
                        rr.getOrNull(MissingMembers.userId) != null
                    )
                }
        }
    }

    suspend fun rowCount(guildId: Long): Long = scopedTransaction {
        GuildXP.select { GuildXP.guildId.eq(guildId) }.count()
    }
}

@Inject
class LevelRolesManager(driverManager: DriverManager) : AbstractLevelRolesManager(driverManager) {

    suspend fun getPrevLevelRole(guildId: Long, level: Long): LevelRolesData? {
        return scopedTransaction {
            LevelRoles.select {
                (LevelRoles.guildId eq guildId)
                    .and(LevelRoles.level less level)
            }.orderBy(LevelRoles.level, SortOrder.DESC).firstOrNull()?.let {
                LevelRolesData.fromResRow(it)
            }
        }
    }
}

@Inject
class TopRolesManager(driverManager: DriverManager) : AbstractTopRolesManager(driverManager) {
    suspend fun getTopRoles(guildId: Long, maxLevel: Long): List<TopRolesData> {
        return scopedTransaction {
            TopRoles.select {
                TopRoles.guildId.eq(guildId).and(TopRoles.minLevelTop.lessEq(maxLevel))
            }.orderBy(TopRoles.minLevelTop, SortOrder.DESC).map {
                TopRolesData.fromResRow(it)
            }
        }
    }
}

@Inject
class TopRoleMemberManager(driverManager: DriverManager) : AbstractTopRoleMemberManager(driverManager)

@Inject
class XPManager(
    val driverManager: DriverManager,
    val globalXPManager: GlobalXPManager,
    val guildXPManager: GuildXPManager,
    val levelRolesManager: LevelRolesManager,
    val topRolesManager: TopRolesManager,
    val topRolesMemberManager: TopRoleMemberManager
) {

    suspend fun getGlobalXP(userSnowflake: ISnowflake): Long {
        return globalXPManager.getById(userSnowflake.idLong)?.xp ?: 0L
    }

    suspend fun getGuildXP(guildId: ISnowflake, userSnowflake: ISnowflake): Long {
        return guildXPManager.getById(guildId.idLong, userSnowflake.idLong)?.xp ?: 0L
    }

    suspend fun setGlobalXP(userSnowflake: ISnowflake, xp: Long) {
        globalXPManager.store(GlobalXPData(userSnowflake.idLong, xp))
    }

    suspend fun setGuildXP(guildId: ISnowflake, userSnowflake: ISnowflake, xp: Long) {
        guildXPManager.store(GuildXPData(guildId.idLong, userSnowflake.idLong, xp))
    }

    suspend fun increaseAllXP(member: Member, amount: Long) {
        val guild = member.guild
        val user = member.user
        val oldGuildXPData = guildXPManager.getById(guild.idLong, user.idLong)
            ?: GuildXPData(guild.idLong, user.idLong, amount)
        val oldGlobalXP = globalXPManager.getById(user.idLong)?.xp ?: 0
        setGlobalXP(user, oldGlobalXP + amount)
        val newGuildXPData = oldGuildXPData.apply {
            xp += amount
        }
        guildXPManager.store(newGuildXPData)
        val botManager by KoinUtil.inject<ExtensibleBot>()
        val event = GuildXPChangeEvent(oldGuildXPData.xp, newGuildXPData.xp, member)
        botManager.send(event)
    }

    suspend fun getTopRoles(guildId: Long, minLevel: Long): List<TopRolesData> {
        return topRolesManager.getTopRoles(guildId, minLevel)
    }

    suspend fun getMsgXPCooldown(userSnowflake: ISnowflake): Long {
        return driverManager.getCacheEntry("messageXPCooldown:${userSnowflake}")?.toLong() ?: 0
    }

    fun setMsgXPCooldown(userSnowflake: ISnowflake, cooldown: Duration) {
        driverManager.setCacheEntry(
            "messageXPCooldown:${userSnowflake}",
            (System.currentTimeMillis() + cooldown.inWholeMilliseconds).toString(),
            cooldown.inWholeMinutes.toInt() + 1, TimeUnit.MINUTES
        )
    }

    suspend fun getGuildPosition(guildId: Long, userId: Long): AugmentedGuildXPData? {
        return guildXPManager.getPosition(guildId, userId)
    }

    suspend fun getMemberAtPos(guildId: Long, position: Long): AugmentedGuildXPData? {
        return guildXPManager.getAtPosition(guildId, position)
    }
}
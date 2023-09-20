package me.melijn.bot.database.manager

import com.kotlindiscord.kord.extensions.ExtensibleBot
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.GlobalXP
import me.melijn.bot.database.model.GuildXP
import me.melijn.bot.database.model.LevelRoles
import me.melijn.bot.database.model.TopRoles
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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
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

    suspend fun getPosition(guildId: Long, userId: Long): Pair<GuildXPData, Long>? = suspendCoroutine { continuation ->
        @Language("postgresql")
        val query1 =
            """SELECT subq.guild_id, subq.user_id, subq.xp, subq.missing, position
                  |FROM (
                  |    SELECT guild_xp.guild_id, guild_xp.user_id, guild_xp.xp, guild_xp.missing, ROW_NUMBER() over (order by guild_xp.xp desc) as position 
                  |    FROM guild_xp 
                  |    WHERE (guild_xp.guild_id = ?) AND (guild_xp.missing = FALSE)
                  |    ORDER BY guild_xp.xp DESC) subq 
                  |WHERE subq.user_id = ?
                  |""".trimMargin()
        driverManager.executeQuery(query1, { rs ->
            if (rs.next()) {
                println(rs)

                val entry = GuildXPData(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    rs.getBoolean(4)
                )
                val rowNumber = rs.getLong(5)
                continuation.resume(entry to rowNumber)
            } else continuation.resume(null)
        }, guildId, userId)
    }

    suspend fun getAtPosition(guildId: Long, position: Int): GuildXPData? {
        return getTop(guildId, 1, position).firstOrNull()
    }

    suspend fun getTop(guildId: Long, count: Int, offset: Int): List<GuildXPData> {
        return scopedTransaction {
            GuildXP.select {
                GuildXP.guildId.eq(guildId)
            }.orderBy(GuildXP.xp, SortOrder.DESC)
                .limit(count, offset.toLong())
                .map { GuildXPData.fromResRow(it) }
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
class TopRoleMemberManager(driverManager: DriverManager) : AbstractTopRoleMemberManager(driverManager) {

}

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
        guildXPManager.store(GuildXPData(guildId.idLong, userSnowflake.idLong, xp, false))
    }

    suspend fun increaseAllXP(member: Member, amount: Long) {
        val guild = member.guild
        val user = member.user
        val oldGuildXPData = guildXPManager.getById(guild.idLong, user.idLong)
            ?: GuildXPData(guild.idLong, user.idLong, amount, false)
        val oldGlobalXP = globalXPManager.getById(user.idLong)?.xp ?: 0
        setGlobalXP(user, oldGlobalXP + amount)
        val newGuildXPData = oldGuildXPData.apply {
            missing = false
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

    suspend fun getGuildPosition(guildId: Long, userId: Long): Pair<GuildXPData, Long>? {
        return guildXPManager.getPosition(guildId, userId)
    }

    suspend fun getMemberAtPos(guildId: Long, position: Int): GuildXPData? {
        return guildXPManager.getAtPosition(guildId, position)
    }

    suspend fun markMemberMissing(data: GuildXPData) {
        guildXPManager.store(data.apply {
            this.missing = true
        })
    }
}
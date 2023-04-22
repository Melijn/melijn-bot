package me.melijn.bot.database.manager

import com.kotlindiscord.kord.extensions.ExtensibleBot
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.GlobalXP
import me.melijn.bot.database.model.GuildXP
import me.melijn.bot.database.model.LevelRoles
import me.melijn.bot.events.leveling.GuildXPChangeEvent
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.GlobalXPData
import me.melijn.gen.GuildXPData
import me.melijn.gen.LevelRolesData
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.User
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

@Inject
class GlobalXPManager(driverManager: DriverManager) : AbstractGlobalXPManager(driverManager) {

    fun increaseGlobalXP(userSnowflake: ISnowflake, amount: Long) {
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

    fun increaseGuildXP(guildSnowflake: ISnowflake, userSnowflake: ISnowflake, amount: Long) {
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
}

@Inject
class LevelRolesManager(driverManager: DriverManager) : AbstractLevelRolesManager(driverManager) {

    fun getPrevLevelRole(guildId: Long, level: Long): LevelRolesData? {
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
class TopRolesManager(driverManager: DriverManager) : AbstractTopRolesManager(driverManager)

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

    fun getGlobalXP(userSnowflake: ISnowflake): Long {
        return globalXPManager.getById(userSnowflake.idLong)?.xp ?: 0L
    }

    fun getGuildXP(guildId: ISnowflake, userSnowflake: ISnowflake): Long {
        return guildXPManager.getById(guildId.idLong, userSnowflake.idLong)?.xp ?: 0L
    }

    fun setGlobalXP(userSnowflake: ISnowflake, xp: Long) {
        globalXPManager.store(GlobalXPData(userSnowflake.idLong, xp))

    }

    fun setGuildXP(guildId: ISnowflake, userSnowflake: ISnowflake, xp: Long) {
        guildXPManager.store(GuildXPData(guildId.idLong, userSnowflake.idLong, xp))
    }

    suspend fun increaseAllXP(guild: Guild, user: User, amount: Long) {
        val oldGuildXP = guildXPManager.getCachedById(guild.idLong, user.idLong)?.xp ?: 0
        val oldGlobalXP = globalXPManager.getCachedById(user.idLong)?.xp ?: 0
        setGlobalXP(user, oldGlobalXP + amount)
        setGuildXP(guild, user, oldGuildXP + amount)
        val newGuildXP = oldGuildXP + amount
        val botManager by KoinUtil.inject<ExtensibleBot>()
        val event = GuildXPChangeEvent(guild.jda, oldGuildXP, newGuildXP, user, guild)
        botManager.send(event)
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
}
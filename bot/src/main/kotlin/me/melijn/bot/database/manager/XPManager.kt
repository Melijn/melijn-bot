package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.GlobalXP
import me.melijn.bot.database.model.GuildXP
import me.melijn.gen.GlobalXPData
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import net.dv8tion.jda.api.entities.ISnowflake
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
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
class LevelRolesManager(driverManager: DriverManager) : AbstractLevelRolesManager(driverManager)

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

    fun setGlobalXP(userSnowflake: ISnowflake, xp: Long) {
        globalXPManager.store(GlobalXPData(userSnowflake.idLong, xp))
    }

    fun increaseAllXP(guildSnowflake: ISnowflake, userSnowflake: ISnowflake, amount: Long) {
        transaction(driverManager.database) {
            GlobalXP.insertOrUpdate({
                it[userId] = userSnowflake.idLong
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            }, {
                // this[GlobalXP.xp]
            })
            GuildXP.insertOrUpdate({
                it[guildId] = guildSnowflake.idLong
                it[userId] = userSnowflake.idLong
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            }, {
                // this[GuildXP.xp]
            })
        }
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
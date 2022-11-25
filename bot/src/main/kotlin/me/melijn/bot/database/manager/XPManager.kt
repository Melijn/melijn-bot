package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.GlobalXP
import me.melijn.bot.database.model.GuildXP
import me.melijn.gen.GlobalXPData
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

@Inject
class GlobalXPManager(driverManager: DriverManager) : AbstractGlobalXPManager(driverManager) {

    fun increaseGlobalXP(userSnowflake: Snowflake, amount: ULong) {
        scopedTransaction {
            GlobalXP.insertOrUpdate({
                it[userId] = userSnowflake.value
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            })
        }
    }
}

@Inject
class GuildXPManager(driverManager: DriverManager) : AbstractGuildXPManager(driverManager) {

    fun increaseGuildXP(guildSnowflake: Snowflake, userSnowflake: Snowflake, amount: ULong) {
        scopedTransaction {
            GuildXP.insertOrUpdate({
                it[guildId] = guildSnowflake.value
                it[userId] = userSnowflake.value
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

    fun getGlobalXP(userSnowflake: Snowflake): ULong {
        return globalXPManager.getById(userSnowflake.value)?.xp ?: 0UL
    }

    fun setGlobalXP(userSnowflake: Snowflake, xp: ULong) {
        globalXPManager.store(GlobalXPData(userSnowflake.value, xp))
    }

    fun increaseAllXP(guildSnowflake: Snowflake, userSnowflake: Snowflake, amount: ULong) {
        transaction(driverManager.database) {
            GlobalXP.insertOrUpdate({
                it[userId] = userSnowflake.value
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            }, {
                this[GuildXP.xp]
            })
            GuildXP.insertOrUpdate({
                it[guildId] = guildSnowflake.value
                it[userId] = userSnowflake.value
                it[xp] = amount
            }, {
                it[xp] = xp.plus(amount)
            }, {
                this[GuildXP.xp]
            })
        }
    }

    suspend fun getMsgXPCooldown(userSnowflake: Snowflake): Long {
        return driverManager.getCacheEntry("messageXPCooldown:${userSnowflake}")?.toLong() ?: 0
    }

    fun setMsgXPCooldown(userSnowflake: Snowflake, cooldown: Duration) {
        driverManager.setCacheEntry(
            "messageXPCooldown:${userSnowflake}",
            (System.currentTimeMillis() + cooldown.inWholeMilliseconds).toString(),
            cooldown.inWholeMinutes.toInt() + 1, TimeUnit.MINUTES
        )
    }
}
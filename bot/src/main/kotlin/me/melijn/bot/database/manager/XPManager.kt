package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.GlobalXP
import me.melijn.gen.GlobalXPData
import me.melijn.gen.database.manager.AbstractGlobalXPManager
import me.melijn.gen.database.manager.AbstractLevelRolesManager
import me.melijn.gen.database.manager.AbstractTopRoleMemberManager
import me.melijn.gen.database.manager.AbstractTopRolesManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import java.util.concurrent.TimeUnit

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
class LevelRolesManager(driverManager: DriverManager) : AbstractLevelRolesManager(driverManager)

@Inject
class TopRolesManager(driverManager: DriverManager) : AbstractTopRolesManager(driverManager)

@Inject
class TopRoleMemberManager(driverManager: DriverManager) : AbstractTopRoleMemberManager(driverManager)

@Inject
class XPManager(
    val driverManager: DriverManager,
    val globalXPManager: GlobalXPManager,
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

    fun increaseGlobalXP(userSnowflake: Snowflake, amount: ULong) {
        globalXPManager.increaseGlobalXP(userSnowflake, amount)
    }

    suspend fun getMsgXPCooldown(userSnowflake: Snowflake): Long {
        return driverManager.getCacheEntry("messageXPCooldown:${userSnowflake}")?.toLong()?:0
    }

    suspend fun setMsgXPCooldown(userSnowflake: Snowflake, cooldown: Long) {
        driverManager.setCacheEntry("messageXPCooldown:${userSnowflake}", cooldown.toString(), cooldown.toInt(), TimeUnit.MILLISECONDS)
    }
}
package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.annotationprocessors.injector.Inject
import me.melijn.bot.database.DriverManager
import me.melijn.gen.UserBalanceData
import me.melijn.gen.database.manager.AbstractUserBalanceManager

@Inject
class BalanceManager(driverManager: DriverManager) : AbstractUserBalanceManager(driverManager) {

    suspend fun get(id: Snowflake): UserBalanceData {
        return getCachedById(id.value) ?: UserBalanceData(id.value, 0)
    }
}
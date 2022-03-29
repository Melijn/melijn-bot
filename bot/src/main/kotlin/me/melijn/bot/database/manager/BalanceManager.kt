package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.gen.UserBalanceData
import me.melijn.gen.database.manager.AbstractUserBalanceManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class BalanceManager(driverManager: DriverManager) : AbstractUserBalanceManager(driverManager) {

    suspend fun get(id: Snowflake): UserBalanceData {
        return getCachedById(id.value) ?: UserBalanceData(id.value, 0)
    }
}
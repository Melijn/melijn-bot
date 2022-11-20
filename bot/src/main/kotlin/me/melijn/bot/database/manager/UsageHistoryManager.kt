package me.melijn.bot.database.manager

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistoryImpl
import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.UserCommandUsageHistoryData
import me.melijn.gen.UserUsageHistoryData
import me.melijn.gen.database.manager.AbstractUserCommandUsageHistoryManager
import me.melijn.gen.database.manager.AbstractUserUsageHistoryManager
import me.melijn.kordkommons.database.DriverManager
inline val Int.b get() = this.toByte()

@Inject
class UserCommandUsageHistoryManager(override val driverManager: DriverManager) : AbstractUserCommandUsageHistoryManager(driverManager)
@Inject
class UserUsageHistoryManager(override val driverManager: DriverManager) : AbstractUserUsageHistoryManager(driverManager)

@Inject
class UsageHistoryManager(
    private val userCommandUsageHistoryManager: UserCommandUsageHistoryManager,
    private val userUsageHistoryManager: UserUsageHistoryManager,
) {
    private val objectMapper by KoinUtil.inject<ObjectMapper>()

    fun getUserCmdHistDeserialized(userId: ULong, commandId: Int): UsageHistory {
        val byId = userCommandUsageHistoryManager.getById(userId, commandId)
        val barr = byId?.usageHistory ?: return UsageHistoryImpl()
        return objectMapper.readValue<UsageHistoryImpl>(String(barr))
    }

    fun setUserCmdHistSerialized(userId: ULong, commandId: Int, usageHistory: UsageHistory) {
        userCommandUsageHistoryManager.store(
            UserCommandUsageHistoryData(userId, commandId, objectMapper.writeValueAsBytes(usageHistory))
        )
    }

    fun getUserHistDeserialized(userId: ULong): UsageHistory {
        val byId = userUsageHistoryManager.getById(userId)
        val barr = byId?.usageHistory ?: return UsageHistoryImpl()
        return objectMapper.readValue<UsageHistoryImpl>(String(barr))
    }

    fun setUserHistSerialized(userId: ULong, usageHistory: UsageHistory) {
        userUsageHistoryManager.store(
            UserUsageHistoryData(userId, objectMapper.writeValueAsBytes(usageHistory))
        )
    }
}
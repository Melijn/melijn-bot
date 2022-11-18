package me.melijn.bot.database.manager

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistoryImpl
import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.UserCommandUsageHistoryData
import me.melijn.gen.database.manager.AbstractUserCommandUsageHistoryManager
import me.melijn.kordkommons.database.DriverManager
inline val Int.b get() = this.toByte()

@Inject
class UsageHistoryManager(override val driverManager: DriverManager) :
    AbstractUserCommandUsageHistoryManager(driverManager) {
    private val objectMapper by KoinUtil.inject<ObjectMapper>()

    fun getDeserialized(userId: ULong, commandId: Int): UsageHistory {
        val byId = getById(userId, commandId)
        val barr = byId?.usageHistory ?: return UsageHistoryImpl()
        return objectMapper.readValue<UsageHistoryImpl>(String(barr))
    }

    fun setSerialized(userId: ULong, commandId: Int, usageHistory: UsageHistory) {
        store(UserCommandUsageHistoryData(userId, commandId, objectMapper.writeValueAsBytes(usageHistory)))
    }
}
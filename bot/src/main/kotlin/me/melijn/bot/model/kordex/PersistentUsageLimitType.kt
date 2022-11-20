package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.UsageLimitType
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistory
import me.melijn.bot.database.manager.CooldownManager
import me.melijn.bot.database.manager.UsageHistoryManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.UserCommandCooldownData
import me.melijn.gen.UserCooldownData

enum class PersistentUsageLimitType : UsageLimitType {

    COMMAND_USER {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getUserCmdCd(context.userId, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.storeUserCmdCd(UserCommandCooldownData(context.userId, context.commandId, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): UsageHistory {
            return usageHistoryManager.getUserCmdHistDeserialized(context.userId, context.commandId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: UsageHistory) {
            usageHistoryManager.setUserCmdHistSerialized(context.userId, context.commandId, usageHistory)
        }
    },
    USER {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getUserCd(context.userId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.storeUserCd(UserCooldownData(context.userId, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): UsageHistory {
             return usageHistoryManager.getUserHistDeserialized(context.userId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: UsageHistory) {
             usageHistoryManager.setUserHistSerialized(context.userId, usageHistory)
        }

    };

    val cooldownManager by KoinUtil.inject<CooldownManager>()
    val usageHistoryManager by KoinUtil.inject<UsageHistoryManager>()

    val DiscriminatingContext.userId
            get() = this.user.id.value
    
    val DiscriminatingContext.commandId
        get() = this.event.command.getFullName().hashCode()
    
}
package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.UsageLimitType
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistory
import me.melijn.bot.database.manager.CooldownManager
import me.melijn.bot.database.manager.UsageHistoryManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.UserCommandCooldownData

enum class PersistentUsageLimitType : UsageLimitType {

    COMMAND_USER {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getById(context.userId, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.store(UserCommandCooldownData(context.userId, context.commandId, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): UsageHistory {
            return usageHistoryManager.getDeserialized(context.userId, context.commandId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: UsageHistory) {
            usageHistoryManager.setSerialized(context.userId, context.commandId, usageHistory)
        }
    },
    USER {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getById(context.userId)?.until ?: 0
        }

        override fun getUsageHistory(context: DiscriminatingContext): UsageHistory {
            TODO("Not yet implemented")
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            TODO("Not yet implemented")
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: UsageHistory) {
            TODO("Not yet implemented")
        }

    };

    val cooldownManager by KoinUtil.inject<CooldownManager>()
    val usageHistoryManager by KoinUtil.inject<UsageHistoryManager>()

    val DiscriminatingContext.userId
            get() = this.user.id.value
    
    val DiscriminatingContext.commandId
        get() = this.event.command.getFullName().hashCode()
    
}
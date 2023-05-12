package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.CommandLimitType
import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.CooldownHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitHistory
import kotlinx.datetime.Instant
import me.melijn.bot.database.manager.CooldownManager
import me.melijn.bot.database.manager.UsageHistoryManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.*

enum class PersistentUsageLimitType : CommandLimitType {

    USER_COMMAND {
        override suspend fun getCooldown(context: DiscriminatingContext): Instant {
            val res = cooldownManager.getUserCmdCd(context.userId, context.commandId)?.until ?: 0
            return Instant.fromEpochMilliseconds(res)
        }

        override fun getCooldownUsageHistory(context: DiscriminatingContext): CooldownHistory {
            TODO("Not yet implemented")
        }

        override suspend fun setCooldown(context: DiscriminatingContext, until: Instant) {
            cooldownManager.storeUserCmdCd(UserCommandCooldownData(context.userId, context.commandId, until.toEpochMilliseconds()))
        }

        override fun setCooldownUsageHistory(context: DiscriminatingContext, usageHistory: CooldownHistory) {
            TODO("Not yet implemented")
        }

        fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getUserCmdHistory(context.userId, context.commandId)
        }

        fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setUserCmdHistSerialized(context.userId, context.commandId, usageHistory)
        }

        override fun getRateLimitUsageHistory(context: DiscriminatingContext): RateLimitHistory {
            TODO("Not yet implemented")
        }

        override fun setRateLimitUsageHistory(context: DiscriminatingContext, rateLimitHistory: RateLimitHistory) {
            TODO("Not yet implemented")
        }
    },

    USER {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getUserCd(context.userId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.storeUserCd(UserCooldownData(context.userId, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getUserHistory(context.userId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setUserHistory(context.userId, usageHistory)
        }
    },

    GUILD_USER {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return context.guildId?.let { cooldownManager.getGuildUserCd(it, context.userId)?.until } ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.let {
                cooldownManager.storeGuildUserCd(GuildUserCooldownData(it, context.userId, until))
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return context.guildId?.let {
                usageHistoryManager.getGuildUserHistory(it, context.userId)
            } ?: emptyHistory
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            context.guildId?.let { usageHistoryManager.setGuildUserHistory(it, context.userId, usageHistory) }
        }
    },

    CHANNEL {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getChannelCd(context.channel.idLong)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.storeChannelCd(ChannelCooldownData(context.channel.idLong, context.guildId, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelHistory(context.channel.idLong)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelHistory(context.channel.idLong, usageHistory)
        }
    },

    GUILD {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return context.guildId?.let { cooldownManager.getGuildCd(it)?.until } ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.let { guildId ->
                cooldownManager.storeGuildCd(GuildCooldownData(guildId, until))
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return context.guildId?.let { usageHistoryManager.getGuildHistory(it) } ?: emptyHistory
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            context.guildId?.let { usageHistoryManager.setGuildHistory(it, usageHistory) }
        }


    },
    GUILD_USER_COMMANDID {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return context.guildId?.let {
                cooldownManager.getGuildUserCmdCd(it, context.userId, context.commandId)?.until
            } ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.let {
                cooldownManager.storeGuildUserCmdCd(GuildUserCommandCooldownData(it, context.userId, context.commandId, until))
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return context.guildId?.let { usageHistoryManager.getGuildHistory(it) } ?: emptyHistory
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            context.guildId?.let {
                usageHistoryManager.setGuildUserCommandUsageHistory(it, context.userId, context.commandId, usageHistory)
            }
        }
    },
    CHANNEL_COMMANDID {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getChannelCmdCd(context.channel.idLong, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.let {
                cooldownManager.storeChannelCmdCd(
                    ChannelCommandCooldownData(context.channel.idLong, it, context.commandId, until)
                )
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelHistory(context.channel.idLong)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelCommandHistory(context.channel.idLong, context.commandId, usageHistory)
        }
    },
    CHANNEL_USER_COMMANDID {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
           return cooldownManager.getChannelUserCmdCd(context.channel.idLong, context.userId, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.let {
                cooldownManager.storeChannelUserCmdCd(
                    ChannelUserCommandCooldownData(context.channel.idLong, it, context.userId, context.commandId, until)
                )
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelUserCommandUsageHistory(context.channel.idLong, context.userId, context.commandId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelUserCommandUsageHistory(context.channel.idLong, context.userId, context.commandId, usageHistory)
        }
    },
    CHANNEL_USER {
        override suspend fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getChannelUserCd(context.channel.idLong, context.userId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.let {
                cooldownManager.storeChannelUserCd(
                    ChannelUserCooldownData(context.channel.idLong, it, context.userId, until)
                )
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelUserUsageHistory(context.channel.idLong, context.userId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelUserUsageHistory(context.channel.idLong, context.userId, usageHistory)
        }
    };

    val cooldownManager by KoinUtil.inject<CooldownManager>()
    val usageHistoryManager by KoinUtil.inject<UsageHistoryManager>()
    val emptyHistory = MelUsageHistory(emptyList(), emptyList(), false, emptyList())

    val DiscriminatingContext.userId
        get() = this.user.idLong

    val DiscriminatingContext.commandId
        get() = this.event.command.getFullName().hashCode()
}
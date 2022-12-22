package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import me.melijn.bot.database.manager.CooldownManager
import me.melijn.bot.database.manager.UsageHistoryManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.*

enum class PersistentUsageLimitType : MelUsageLimitType {

    USER_COMMAND {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getUserCmdCd(context.userId, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.storeUserCmdCd(UserCommandCooldownData(context.userId, context.commandId, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getUserCmdHistory(context.userId, context.commandId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
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

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getUserHistory(context.userId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setUserHistory(context.userId, usageHistory)
        }
    },

    GUILD_USER {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return context.guildId?.value?.let { cooldownManager.getGuildUserCd(it, context.userId)?.until } ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.value?.let {
                cooldownManager.storeGuildUserCd(GuildUserCooldownData(it, context.userId, until))
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return context.guildId?.value?.let {
                usageHistoryManager.getGuildUserHistory(it, context.userId)
            } ?: emptyHistory
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            context.guildId?.value?.let { usageHistoryManager.setGuildUserHistory(it, context.userId, usageHistory) }
        }
    },

    CHANNEL {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getChannelCd(context.channel.id.value)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            cooldownManager.storeChannelCd(ChannelCooldownData(context.channel.id.value, context.guildId?.value, until))
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelHistory(context.channel.id.value)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelHistory(context.channel.id.value, usageHistory)
        }
    },

    GUILD {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return context.guildId?.value?.let { cooldownManager.getGuildCd(it)?.until } ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.value?.let { guildId ->
                cooldownManager.storeGuildCd(GuildCooldownData(guildId, until))
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return context.guildId?.value?.let { usageHistoryManager.getGuildHistory(it) } ?: emptyHistory
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            context.guildId?.value?.let { usageHistoryManager.setGuildHistory(it, usageHistory) }
        }


    },
    GUILD_USER_COMMANDID {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return context.guildId?.value?.let {
                cooldownManager.getGuildUserCmdCd(it, context.userId, context.commandId)?.until
            } ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.value?.let {
                cooldownManager.storeGuildUserCmdCd(GuildUserCommandCooldownData(it, context.userId, context.commandId, until))
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return context.guildId?.value?.let { usageHistoryManager.getGuildHistory(it) } ?: emptyHistory
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            context.guildId?.value?.let {
                usageHistoryManager.setGuildUserCommandUsageHistory(it, context.userId, context.commandId, usageHistory)
            }
        }
    },
    CHANNEL_COMMANDID {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getChannelCmdCd(context.channel.id.value, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.value?.let {
                cooldownManager.storeChannelCmdCd(
                    ChannelCommandCooldownData(context.channel.id.value, it, context.commandId, until)
                )
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelHistory(context.channel.id.value)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelCommandHistory(context.channel.id.value, context.commandId, usageHistory)
        }
    },
    CHANNEL_USER_COMMANDID {
        override fun getCooldown(context: DiscriminatingContext): Long {
           return cooldownManager.getChannelUserCmdCd(context.channel.id.value, context.userId, context.commandId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.value?.let {
                cooldownManager.storeChannelUserCmdCd(
                    ChannelUserCommandCooldownData(context.channel.id.value, it, context.userId, context.commandId, until)
                )
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelUserCommandUsageHistory(context.channel.id.value, context.userId, context.commandId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelUserCommandUsageHistory(context.channel.id.value, context.userId, context.commandId, usageHistory)
        }
    },
    CHANNEL_USER {
        override fun getCooldown(context: DiscriminatingContext): Long {
            return cooldownManager.getChannelUserCd(context.channel.id.value, context.userId)?.until ?: 0
        }

        override fun setCooldown(context: DiscriminatingContext, until: Long) {
            context.guildId?.value?.let {
                cooldownManager.storeChannelUserCd(
                    ChannelUserCooldownData(context.channel.id.value, it, context.userId, until)
                )
            }
        }

        override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory {
            return usageHistoryManager.getChannelUserUsageHistory(context.channel.id.value, context.userId)
        }

        override fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory) {
            usageHistoryManager.setChannelUserUsageHistory(context.channel.id.value, context.userId, usageHistory)
        }
    };

    val cooldownManager by KoinUtil.inject<CooldownManager>()
    val usageHistoryManager by KoinUtil.inject<UsageHistoryManager>()
    val emptyHistory = MelUsageHistory(emptyList(), emptyList(), false, emptyList())

    val DiscriminatingContext.userId
        get() = this.user.id.value

    val DiscriminatingContext.commandId
        get() = this.event.command.getFullName().hashCode()
}
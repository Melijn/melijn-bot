package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.DefaultRateLimiter
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitType
import me.melijn.bot.database.manager.UsageHistoryManager
import me.melijn.bot.utils.KoinUtil

class MelijnRatelimiter : DefaultRateLimiter() {

    val usageHistoryManager by KoinUtil.inject<UsageHistoryManager>()

    override suspend fun checkCommandRatelimit(context: DiscriminatingContext): Boolean {
        val result = super.checkCommandRatelimit(context)
        usageHistoryManager.updateUsage(context.guildId, context.channel.id, context.user.id, context.event.command.getFullName().hashCode())
        return result
    }

    override suspend fun getMessage(
        context: DiscriminatingContext,
        discordTimeStamp: String,
        type: RateLimitType,
    ): String {
        val locale = context.locale()
        val translationsProvider = context.event.command.translationsProvider
        val commandName = context.event.command.getFullName(locale)
        return when (type) {
            PersistentUsageLimitType.COMMAND_USER -> translationsProvider.translate(
                "ratelimit.notifier.commandUser",
                locale,
                replacements = arrayOf(discordTimeStamp, commandName)
            )
            PersistentUsageLimitType.USER -> translationsProvider.translate(
                "ratelimit.notifier.globalUser",
                locale,
                replacements = arrayOf(discordTimeStamp)
            )

            else -> super.getMessage(context, discordTimeStamp, type)
        }
    }
}
package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.CooldownProvider
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.CooldownType
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.DefaultCooldownHandler
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.DefaultCooldownProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import me.melijn.gen.uselimits.PersistentUsageLimitType
import net.dv8tion.jda.api.utils.TimeFormat

class MelijnCooldownHandler : DefaultCooldownHandler() {

    /** Cooldown settings provider, collects configured settings for cooldowns. **/
    override var cooldownProvider: CooldownProvider = DefaultCooldownProvider()

    /** @return Message about what cooldown has been hit. **/
    override suspend fun getMessage(
        context: DiscriminatingContext,
        cooldownUntil: Instant,
        type: CooldownType,
    ): String {
        val locale = context.locale()
        val translationsProvider = context.event.command.translationsProvider
        val commandName = context.event.command.getFullName(locale)
        val discordTimeStamp = TimeFormat.RELATIVE.format(cooldownUntil.toJavaInstant())

        return when (type) {
            PersistentUsageLimitType.UserCommand -> translationsProvider.translate(
                "cooldown.notifier.commandUser",
                locale,
                replacements = arrayOf(discordTimeStamp, commandName)
            )

            PersistentUsageLimitType.ChannelUserCommand -> translationsProvider.translate(
                "cooldown.notifier.commandUserChannel",
                locale,
                replacements = arrayOf(discordTimeStamp, commandName, context.channel.asMention)
            )

            PersistentUsageLimitType.GuildUserCommand -> translationsProvider.translate(
                "cooldown.notifier.commandUserGuild",
                locale,
                replacements = arrayOf(discordTimeStamp, commandName)
            )

            PersistentUsageLimitType.User -> translationsProvider.translate(
                "cooldown.notifier.globalUser",
                locale,
                replacements = arrayOf(discordTimeStamp)
            )

            PersistentUsageLimitType.ChannelUser -> translationsProvider.translate(
                "cooldown.notifier.globalUserChannel",
                locale,
                replacements = arrayOf(discordTimeStamp, context.channel.asMention)
            )

            PersistentUsageLimitType.GuildUser -> translationsProvider.translate(
                "cooldown.notifier.globalUserGuild",
                locale,
                replacements = arrayOf(discordTimeStamp)
            )

            PersistentUsageLimitType.Channel -> translationsProvider.translate(
                "cooldown.notifier.globalChannel",
                locale,
                replacements = arrayOf(discordTimeStamp, commandName)
            )

            PersistentUsageLimitType.Guild -> translationsProvider.translate(
                "cooldown.notifier.globalGuild",
                locale,
                replacements = arrayOf(discordTimeStamp)
            )

            else -> super.getMessage(context, cooldownUntil, type)
        }
    }
}
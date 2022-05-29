package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.commands.CommandContext
import dev.kord.common.Color
import me.melijn.bot.database.manager.CommandEmbedColorManager
import me.melijn.bot.utils.KoinUtil.inject
import kotlin.contracts.ExperimentalContracts


/**
 * Adds an embed to the message, configured by the [block]. A message can have up to 10 embeds.
 */
@OptIn(ExperimentalContracts::class)
public suspend inline fun CommandContext.determineColor(fallback: Color = Color(161, 180, 237)): Color {
    val commandEmbedColorManager by inject<CommandEmbedColorManager>()
    val user = this.getUser()?.id ?: return fallback
    val guild = this.getGuild()
    val channel = this.getChannel().id

    var color: Color? = null
    if (guild != null) {
        val roles = guild.getMember(user).roleIds



    }


    return commandEmbedColorManager.getColor() ?: fallback
}
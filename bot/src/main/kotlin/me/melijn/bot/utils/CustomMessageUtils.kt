package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.commands.CommandContext
import dev.kord.common.Color
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import me.melijn.bot.database.manager.CommandEmbedColorManager
import me.melijn.bot.utils.KoinUtil.inject


context(CommandContext, MessageCreateBuilder)
suspend inline fun embedWithColor(fallback: Color = Color(161, 180, 237), embedBuilder: EmbedBuilder.() -> Unit) {
    val commandEmbedColorManager by inject<CommandEmbedColorManager>()
    val user = this@CommandContext.getUser()
    val guild = this@CommandContext.getGuild()

    this@MessageCreateBuilder.embed {
        color = user?.id?.let { commandEmbedColorManager.getColor(it) }
            ?: guild?.id?.let { commandEmbedColorManager.getColor(it) }
                ?: fallback
        embedBuilder(this)
    }
}
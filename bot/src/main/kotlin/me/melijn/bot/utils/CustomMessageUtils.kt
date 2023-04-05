package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.commands.CommandContext
import dev.minn.jda.ktx.messages.InlineEmbed
import dev.minn.jda.ktx.messages.InlineMessage
import me.melijn.bot.database.manager.CommandEmbedColorManager
import me.melijn.bot.utils.KoinUtil.inject
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.awt.Color


context(CommandContext, InlineMessage<MessageCreateData>)
suspend inline fun embedWithColor(fallback: Color = Color(161, 180, 237), embedBuilder: InlineEmbed.() -> Unit) {
    val commandEmbedColorManager by inject<CommandEmbedColorManager>()
    val userColor = commandEmbedColorManager.getColor(this@CommandContext.user)
    val guildColor = this@CommandContext.guild?.let { commandEmbedColorManager.getColor(it) }

    this@InlineMessage.embed {
        color = (userColor ?: guildColor ?: fallback).rgb
        embedBuilder(this)
    }
}
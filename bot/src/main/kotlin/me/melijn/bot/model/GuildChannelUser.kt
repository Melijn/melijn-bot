package me.melijn.bot.model

data class GuildChannelUser(
    val guildId: Long,
    val channelId: Long,
    val userId: Long
)
@file:OptIn(ExperimentalUnsignedTypes::class)

package me.melijn.bot.database.model

import me.melijn.bot.model.enums.LogChannelType
import org.jetbrains.exposed.sql.Table

object LogChannels : Table("log_channels") {
    val guildId = ulong("guild_id")
    val type = customEnumeration("channel_type", null, { LogChannelType.valueOf(it.toString()) }, { it.name })
    val channelId = ulong("channel_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, type)
}
package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import me.melijn.bot.model.enums.LogChannelType
import org.jetbrains.exposed.sql.Table

@TableModel(true)
@CreateTable
object LogChannels : Table("log_channels") {
    val guildId = long("guild_id")
    val type = enumeration<LogChannelType>("channel_type")
    val channelId = long("channel_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, type)
}
package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
object VoiceSessions : Table("voice_sessions") {

    val guildId = long("guild_id")
    val channelId = long("channel_id")
    val userId = long("user_id")
    val joined = timestamp("joined")
    val left = timestamp("left").nullable()

    init {
        index(true, userId, joined)
    }

}
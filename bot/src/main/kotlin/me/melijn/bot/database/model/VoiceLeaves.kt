package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.duration
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
object VoiceLeaves : Table("voice_leaves") {

    val guildId = long("guild_id")
    val channelId = long("channel_id")
    val userId = long("user_id").index()
    val timestamp = timestamp("timestamp")
    val timeSpent = duration("time_spent").nullable()

}
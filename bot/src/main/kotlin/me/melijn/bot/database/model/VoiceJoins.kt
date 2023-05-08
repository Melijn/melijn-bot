package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
object VoiceJoins : Table("voice_joins") {

    val guildId = long("guild_id")
    val channelId = long("channel_id")
    val userId = long("user_id")
    val timestamp = timestamp("timestamp")

//    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, timestamp)

}
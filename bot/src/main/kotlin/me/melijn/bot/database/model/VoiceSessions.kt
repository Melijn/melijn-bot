package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
@TableModel(true)
object VoiceSessions : Table("voice_sessions") {

    val guildId = long("guild_id")
    val channelId = long("channel_id")
    val userId = long("user_id")
    val joined = timestamp("joined")
    val left = timestamp("left").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, channelId, userId, joined)

    init {
        index(true, userId, joined)
    }

}
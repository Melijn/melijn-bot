package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@TableModel(true)
object BotRestartTrackQueue : Table("bot_restart_track_queue") {

    val guildId = long("guild_id")
    val voiceChannelId = long("voice_channel_id")
    val playerPosition = long("position")
    val paused = bool("paused")
    val looped = bool("looped")
    val loopedQueue = bool("looped_queue")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId)

    init {
        index(true, guildId)
    }
}

@CreateTable
@TableModel(true)
object BotRestartTrackEntry : TrackJoinTable("bot_restart_track_entry") {

    val guildId = long("guild_id")
    override val trackId = uuid("track_id").references(Track.trackId)
    val position = integer("position")

    val userId = long("user_id")

    val addedAt = datetime("added_time")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, trackId)

    init {
        index(false, guildId)
    }
}


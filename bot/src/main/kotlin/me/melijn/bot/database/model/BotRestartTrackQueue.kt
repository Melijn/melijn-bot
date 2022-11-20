package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@CreateTable
@Cacheable
object BotRestartTrackQueue : Table("bot_restart_track_queue") {

    val guildId = ulong("guild_id")
    val voiceChannelId = ulong("voice_channel_id")
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
@Cacheable
object BotRestartTrackEntry : TrackJoinTable("bot_restart_track_entry") {

    val guildId = ulong("guild_id")
    override val trackId = uuid("track_id").references(Track.trackId)
    val position = integer("position")

    val userId = ulong("user_id")

    val addedAt = datetime("added_time")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, trackId)

    init {
        index(false, guildId)
    }
}


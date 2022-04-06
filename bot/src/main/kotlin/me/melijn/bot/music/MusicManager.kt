package me.melijn.bot.music

import dev.kord.core.behavior.GuildBehavior
import dev.schlaubi.lavakord.kord.getLink
import me.melijn.bot.Melijn
import me.melijn.bot.utils.Log
import java.util.concurrent.ConcurrentHashMap

object MusicManager {

    val guildMusicPlayers: ConcurrentHashMap<ULong, TrackManager> = ConcurrentHashMap()
    private val logger by Log

    fun GuildBehavior.getTrackManager(): TrackManager {
        guildMusicPlayers[id.value]?.run { return this }
        val trackManager = TrackManager(this.getLink(Melijn.lavalink))
        logger.info { "New trackManager for $id" }
        guildMusicPlayers[id.value] = trackManager
        return trackManager
    }
}
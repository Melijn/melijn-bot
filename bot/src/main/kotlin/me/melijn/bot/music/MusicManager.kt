package me.melijn.bot.music

import dev.kord.core.behavior.GuildBehavior
import dev.schlaubi.lavakord.audio.Node
import dev.schlaubi.lavakord.kord.getLink
import kotlinx.coroutines.delay
import me.melijn.bot.Melijn
import me.melijn.bot.utils.Log
import me.melijn.kordkommons.async.TaskManager
import java.util.concurrent.ConcurrentHashMap

object MusicManager {

    val guildMusicPlayers: ConcurrentHashMap<ULong, TrackManager> = ConcurrentHashMap()
    private val logger by Log

    fun GuildBehavior.getTrackManager(): TrackManager {
        guildMusicPlayers[id.value]?.run { return this }
        val link = this.getLink(Melijn.lavalink)
        val trackManager = TrackManager(link)
        logger.info { "New trackManager for $id" }
        guildMusicPlayers[id.value] = trackManager
        return trackManager
    }

    suspend fun setupReconnects(node: Node) {
        TaskManager.async {
            var lastUptime = 0L
            var available = false
            while (true) {
                val nodeUptime = node.lastStatsEvent?.uptime


                if (!node.available && available) {
                    available = false
                    logger.warn { "Node ${node.name} went unavailable!" }
                    val currentPlayers = guildMusicPlayers.filter { it.value.link.node == node }
                    currentPlayers.forEach { (_, u) ->
                        u.onPotentialNodeRestart()
                    }
                    logger.warn { "Backed up ${currentPlayers.size} player states!" }
                } else {
                    if (node.available && !available) {
                        logger.info { "Node ${node.name} back available!" }
                    }
                    available = node.available
                }

                if (nodeUptime != null && node.available) {
                    if (nodeUptime < lastUptime) { // node restarted !
                        logger.warn { "Node ${node.name} apparently restarted (checked using node uptime)!" }
                        val currentPlayers = guildMusicPlayers.filter { it.value.link.node == node }
                        for ((_, u) in currentPlayers) {
                            u.onNodeRestarted()
                        }
                        logger.info { "Loaded ${currentPlayers.size} player states from back up!" }
                    }
                    lastUptime = nodeUptime
                }

                delay(2_000)
            }
        }
    }
}
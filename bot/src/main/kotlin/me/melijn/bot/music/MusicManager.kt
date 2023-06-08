package me.melijn.bot.music

import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.audio.Node
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.melijn.bot.Melijn
import me.melijn.bot.database.manager.BotRestartTrackEntryManager
import me.melijn.bot.database.manager.BotRestartTrackQueueManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.Log
import me.melijn.gen.BotRestartTrackQueueData
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import java.util.concurrent.ConcurrentHashMap

object MusicManager {

    private val guildMusicPlayers: ConcurrentHashMap<Long, TrackManager> = ConcurrentHashMap()
    private val logger by Log

    fun Guild.getTrackManager(): TrackManager {
        guildMusicPlayers[idLong]?.run { return this }
        val link = Melijn.lavalink.getLink(idLong.toULong())
        val trackManager = TrackManager(link)
        logger.info { "New trackManager for $id" }
        guildMusicPlayers[idLong] = trackManager
        return trackManager
    }

    suspend fun Node.registerReconnectHandler() = TaskScope.launch {
        val node = this@registerReconnectHandler
        var lastUptime = 0L
        var available = false
        while (true) {
            val nodeUptime = node.lastStatsEvent?.uptime
            val isAvailable = node.available
            val nodeName = node.name

            if (!isAvailable && available) {
                available = false
                logger.warn { "Node $nodeName went unavailable!" }
                val currentPlayers = guildMusicPlayers.filter { it.value.link.node == node }
                currentPlayers.forEach { (_, tm) ->
                    tm.onPotentialNodeRestart()
                }
                logger.warn { "Backed up ${currentPlayers.size} player states!" }
            } else {
                if (isAvailable && !available) {
                    logger.info { "Node $nodeName back available!" }
                }
                available = isAvailable
            }

            if (nodeUptime != null && isAvailable) {
                if (nodeUptime < lastUptime) { // node restarted !
                    logger.warn { "Node $nodeName apparently restarted (checked using node uptime)!" }
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

    fun recoverMusic(shard: JDA) = TaskScope.launch {
        val shardId = shard.shardInfo.shardId
        val trackEntryManager by KoinUtil.inject<BotRestartTrackEntryManager>()
        val trackQueueManager by KoinUtil.inject<BotRestartTrackQueueManager>()
        val queue = trackQueueManager.getAll(shardId)

        queue.forEach { queueData ->
            val tracks = trackEntryManager.getMelijnTracks(queueData.guildId)
            val guild = shard.getGuildById(queueData.guildId) ?: return@launch
            val tm = guild.getTrackManager()

            tm.link.connectAudio(queueData.voiceChannelId.toULong())

            tracks.forEach { track ->
                tm.queue(track, QueuePosition.BOTTOM)
            }
            tm.player.pause(queueData.paused)
            tm.player.seekTo(queueData.playerPosition)
        }

        trackQueueManager.deleteAll(shardId)
        trackEntryManager.deleteAll(shardId)

        logger.info { "Done recovering music for shard #${shardId}" }
    }

    suspend fun musicBotShutdownHandler() {
        val trackQueueManager = getKoin().getOrNull<BotRestartTrackQueueManager>() ?: return
        val trackEntryManager = getKoin().getOrNull<BotRestartTrackEntryManager>() ?: return

        guildMusicPlayers.forEach { (guildId, trackManager) ->
            if (trackManager.link.state in arrayOf(Link.State.CONNECTED, Link.State.CONNECTING)
                && (trackManager.playingTrack != null || trackManager.queue.size != 0)
            ) {
                // save stuff
                val playingTrack = trackManager.playingTrack
                val queue = trackManager.queue
                val vcId = trackManager.link.lastChannelId?.toLong() ?: run {
                    logger.warn { "No last voice-channel id for guild: $guildId" }
                    return@forEach
                }

                trackQueueManager.store(
                    BotRestartTrackQueueData(
                        guildId, vcId, trackManager.player.position, trackManager.player.paused,
                        trackManager.looped, trackManager.loopedQueue
                    )
                )

                if (playingTrack != null) {
                    trackEntryManager.newTrack(guildId, 0, playingTrack)
                }
                queue.indexedForEach { i, track ->
                    trackEntryManager.newTrack(guildId, i + 1, track)
                }

                trackManager.link.destroy()
            }
        }
    }
}
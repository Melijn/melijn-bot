package me.melijn.bot.services


import dev.minn.jda.ktx.events.listener
import kotlinx.coroutines.launch
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.BotRestartTrackEntryManager
import me.melijn.bot.database.manager.BotRestartTrackQueueManager
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Log
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.sharding.ShardManager

@Inject(true)
class StartupListener {

    val logger by Log

    init {
        val kord by inject<ShardManager>()
        val botRestartTrackEntryManager by inject<BotRestartTrackEntryManager>()
        val botRestartTrackQueueManager by inject<BotRestartTrackQueueManager>()
        var loaded = false

        kord.listener<ReadyEvent> {
            val shardId = it.jda.shardInfo.shardId
            logger.info { "Shard #${shardId} is ready" }

            if (!loaded && kord.shards.all { shard -> shard.guilds.isNotEmpty() }) {
                logger.info { "All shards are ready" }
                loaded = true
                val attendance by inject<AttendanceService>()
                attendance.javaClass
            }

            val queue = botRestartTrackQueueManager.getAll(shardId)
            TaskScope.launch {
                queue.forEach { queueData ->
                    val tracks = botRestartTrackEntryManager.getMelijnTracks(queueData.guildId)

                    val guild = kord.getGuildById(queueData.guildId) ?: return@launch

                    val tm = guild.getTrackManager()
                    tm.link.connectAudio(queueData.voiceChannelId.toULong())

                    tracks.forEach {
                        tm.queue(it, QueuePosition.BOTTOM)
                    }
                    tm.player.pause(queueData.paused)
                    tm.player.seekTo(queueData.playerPosition)
                }
                botRestartTrackQueueManager.deleteAll(shardId)
                botRestartTrackEntryManager.deleteAll(shardId)
                logger.info { "Done recovering music for shard #${shardId}" }
            }
        }
    }
}
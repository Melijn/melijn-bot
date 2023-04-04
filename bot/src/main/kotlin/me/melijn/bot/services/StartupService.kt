package me.melijn.bot.services


import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.BotRestartTrackEntryManager
import me.melijn.bot.database.manager.BotRestartTrackQueueManager
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.Log
import me.melijn.kordkommons.async.TaskManager
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.sharding.ShardManager

@Inject(true)
class StartupService {

    val logger by Log

    init {
        val kord by KoinUtil.inject<ShardManager>()
        val botRestartTrackEntryManager by KoinUtil.inject<BotRestartTrackEntryManager>()
        val botRestartTrackQueueManager by KoinUtil.inject<BotRestartTrackQueueManager>()

        kord.listener<ReadyEvent> {
            val shardId = it.jda.shardInfo.shardId
            logger.info { "Shard #${shardId} is ready" }
            val queue = botRestartTrackQueueManager.getAll(shardId)
            TaskManager.async {
                queue.forEach { queueData ->
                    val tracks = botRestartTrackEntryManager.getMelijnTracks(queueData.guildId)

                    val guild = kord.getGuildById(queueData.guildId) ?: return@async

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
package me.melijn.bot.services

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.on
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.BotRestartTrackEntryManager
import me.melijn.bot.database.manager.BotRestartTrackQueueManager
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.Log
import me.melijn.kordkommons.async.TaskManager

@Inject(true)
class StartupService {

    val logger by Log
    init {

        val kord by KoinUtil.inject<Kord>()
        val botRestartTrackEntryManager by KoinUtil.inject<BotRestartTrackEntryManager>()
        val botRestartTrackQueueManager by KoinUtil.inject<BotRestartTrackQueueManager>()

        kord.on<ReadyEvent> {
            val (shardId, _) = kord.gateway.gateways.entries.first { it.value == this.gateway }
            logger.info { "Shard #${shardId} is ready" }
            val queue = botRestartTrackQueueManager.getAll(shardId)
            TaskManager.async {
                queue.forEach { queueData ->
                    val tracks = botRestartTrackEntryManager.getMelijnTracks(queueData.guildId)

                    val guild = kord.getGuild(Snowflake(queueData.guildId)) ?: return@async

                    val tm = guild.getTrackManager()
                    tm.link.connectAudio(queueData.voiceChannelId)

                    tracks.forEach {
                        tm.queue(it, QueuePosition.BOTTOM)
                    }
                }
                botRestartTrackQueueManager.deleteAll(shardId)
                botRestartTrackEntryManager.deleteAll(shardId)
                logger.info { "Done recovering music for shard #${shardId}" }
            }
        }
    }
}
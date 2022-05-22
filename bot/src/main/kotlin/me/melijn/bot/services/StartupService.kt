package me.melijn.bot.services

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.delay
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.BotRestartTrackEntryManager
import me.melijn.bot.database.manager.BotRestartTrackQueueManager
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.utils.KoinUtil
import me.melijn.kordkommons.async.TaskManager

@Inject(true)
class StartupService {

    init {
        val kord by KoinUtil.inject<Kord>()
        val botRestartTrackEntryManager by KoinUtil.inject<BotRestartTrackEntryManager>()
        val botRestartTrackQueueManager by KoinUtil.inject<BotRestartTrackQueueManager>()

        val queue = botRestartTrackQueueManager.getAll()
        queue.forEach {
            TaskManager.async {
                val tracks = botRestartTrackEntryManager.getMelijnTracks(it.guildId)
                delay(10_000)

                val guild = kord.getGuild(Snowflake(it.guildId)) ?: return@async
                val tm = guild.getTrackManager()
                tm.link.connectAudio(it.voiceChannelId)

                tracks.forEach {
                    tm.queue(it, QueuePosition.BOTTOM)
                    println("track $it")
                }
            }
        }
    }
}
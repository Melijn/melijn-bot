package me.melijn.bot.services

import dev.minn.jda.ktx.messages.InlineEmbed
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.AttendanceManager
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.kordkommons.async.RunnableTask
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

val preFetchDuration = 120.seconds

@Inject(true)
class AttendanceService : Service("attendance", preFetchDuration.times(0), preFetchDuration) {

    val attendanceManager by inject<AttendanceManager>()
    val shardManager by inject<ShardManager> ()
    override val service: RunnableTask = RunnableTask {
        val toScheduleClosingTask = TaskScope.async { attendanceManager.getEntriesAboutToClose(preFetchDuration) }
        val toScheduleNotifyTask = TaskScope.async { attendanceManager.getEntriesAboutToNotify(preFetchDuration) }
        val toScheduleReopenTask = TaskScope.async { attendanceManager.getEntriesAboutToReopen(preFetchDuration) }
        val (toScheduleClosing,
            toScheduleNotify,
            toScheduleReopen) = Triple(
            toScheduleClosingTask.await(),
            toScheduleNotifyTask.await(),
            toScheduleReopenTask.await()
        )

        val now = Clock.System.now()

        for (toClose in toScheduleClosing) {
            val message = shardManager.getGuildById(toClose.guildId)
                ?.getTextChannelById(toClose.channelId)
                ?.retrieveMessageById(toClose.messageId)
                ?.awaitOrNull()

            if (message == null) {
                // make the entry ignored by most queries
                attendanceManager.store(toClose.apply {
                    nextMoment = Instant.fromEpochMilliseconds(0)
                    repeating = false
                })
                continue
            }

            val futureCloseMoment = (toClose.nextMoment - toClose.closeOffset!!)
            val waitDuration = futureCloseMoment - now
            val embed = message.embeds.first()

            message.editMessageEmbeds(InlineEmbed(embed).apply {
                title = "[closed] $title"
            }.build()).queueAfter(waitDuration.inWholeSeconds, TimeUnit.SECONDS)
        }

    }
}
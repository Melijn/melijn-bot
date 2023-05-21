package me.melijn.bot.services

import dev.minn.jda.ktx.messages.InlineEmbed
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.AttendanceExtension
import me.melijn.bot.database.manager.AttendanceManager
import me.melijn.bot.database.model.AttendanceState
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Quadruple
import me.melijn.gen.AttendanceData
import me.melijn.kordkommons.async.RunnableTask
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.sharding.ShardManager
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

val preFetchDuration = 120.seconds

@Inject(true)
class AttendanceService : Service("attendance", preFetchDuration.times(0), preFetchDuration) {

    val attendanceManager by inject<AttendanceManager>()
    val shardManager by inject<ShardManager>()
    override val service: RunnableTask = RunnableTask {
        val toScheduleClosingTask = TaskScope.async { attendanceManager.getEntriesAboutToClose(preFetchDuration) }
        val toScheduleHappeningTask = TaskScope.async { attendanceManager.getEntriesAboutToHappen(preFetchDuration) }
        val toScheduleNotifyTask = TaskScope.async { attendanceManager.getEntriesAboutToNotify(preFetchDuration) }
        val toScheduleReopenTask = TaskScope.async { attendanceManager.getEntriesAboutToReopen(preFetchDuration) }
        val (toScheduleClosing,
            toScheduleNotify,
            toScheduleReopen,
            toScheduleHappening) = Quadruple(
            toScheduleClosingTask.await(),
            toScheduleNotifyTask.await(),
            toScheduleReopenTask.await(),
            toScheduleHappeningTask.await(),
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

        for (toNotify in toScheduleNotify) {
            val guild = shardManager.getGuildById(toNotify.guildId)
            val message = guild
                ?.getTextChannelById(toNotify.channelId)
                ?.retrieveMessageById(toNotify.messageId)
                ?.awaitOrNull()

            val role = toNotify.roleId?.let { guild?.getRoleById(it) } ?: continue

            if (message == null) {
                // make the entry ignored by most queries
                attendanceManager.store(toNotify.apply {
                    nextMoment = Instant.fromEpochMilliseconds(0)
                    repeating = false
                })
                continue
            }

            val futureCloseMoment = (toNotify.nextMoment - toNotify.closeOffset!!)
            val waitDuration = futureCloseMoment - now

            message.editMessage("Reminder: ${role.asMention}")
                .queueAfter(waitDuration.inWholeSeconds, TimeUnit.SECONDS)
        }



        for (toReopen in toScheduleReopen) {
            val guild = shardManager.getGuildById(toReopen.guildId)
            val textChannel = guild
                ?.getTextChannelById(toReopen.channelId)
                ?.takeIf {
                    it.guild.selfMember.hasPermission(
                        it,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_EMBED_LINKS
                    )
                }
            val message = textChannel
                ?.retrieveMessageById(toReopen.messageId)
                ?.awaitOrNull()


            val nextMoment = try {
                AttendanceExtension.nextMomentFromCronSchedule(toReopen.schedule!!)
            } catch (t: Throwable) {
                logger.error(t) { "A corrupt cron schedule is trying to get processed" }
                continue
            }

            if (message == null || nextMoment == null) {
                // make the entry ignored by most queries
                attendanceManager.store(toReopen.apply {
                    this.nextMoment = Instant.fromEpochMilliseconds(0)
                    this.repeating = false
                })
                continue
            }

            val timeZone = toReopen.zoneId?.let { ZoneId.of(it) } ?: ZoneId.of("UTC")
            val messageData = AttendanceExtension.getAttendanceMessage(
                toReopen.topic, toReopen.description, nextMoment, timeZone
            )

            val futureCloseMoment = (toReopen.nextMoment - toReopen.closeOffset!!)
            val waitDuration = futureCloseMoment - now

            textChannel.sendMessage(messageData).queueAfter(waitDuration.inWholeSeconds, TimeUnit.SECONDS, {

            })

        }
    }

    fun AttendanceData.nextAvailableState(): AttendanceState? {
        val now = Clock.System.now()
        val closeOffset = this.closeOffset
        val notifyOffset = this.notifyOffset

        when (this.state) {
            AttendanceState.LISTENING -> {
                return if (closeOffset == null && notifyOffset == null) {
                    if (this.nextMoment < now) {
                        AttendanceState.FINISHED
                    } else null
                } else if (closeOffset != null) {
                    if (this.nextMoment - closeOffset < now) {
                        AttendanceState.CLOSED
                    } else null
                } else if (notifyOffset != null) {
                    if (this.nextMoment - notifyOffset < now) {
                        AttendanceState.NOTIFIED
                    } else null
                } else throw IllegalStateException("amogus")
            }

            AttendanceState.CLOSED -> {
                return if (notifyOffset == null || this.roleId == null || notifyOffset > closeOffset!!) {
                    if (this.nextMoment < now) {
                        AttendanceState.FINISHED
                    } else null
                } else {
                    if (this.nextMoment - notifyOffset < now) {
                        AttendanceState.NOTIFIED
                    } else null
                }
            }

            AttendanceState.NOTIFIED -> {
                return if (closeOffset == null || closeOffset > notifyOffset!!) {
                    if (this.nextMoment < now) {
                        AttendanceState.FINISHED
                    } else null
                } else {
                    if (this.nextMoment - closeOffset < now) {
                        AttendanceState.CLOSED
                    } else null
                }
            }

            AttendanceState.FINISHED -> return null
        }
    }
}
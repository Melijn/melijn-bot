package me.melijn.bot.events.buttons

import com.kotlindiscord.kord.extensions.utils.hasRole
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.messages.InlineEmbed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.AttendanceManager
import me.melijn.bot.database.manager.AttendeesManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.gen.AttendanceData
import me.melijn.gen.AttendeesData
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Inject(true)
class AttendanceButtonHandler {

    val attendanceManager by inject<AttendanceManager>()
    val attendeesManager by inject<AttendeesManager>()

    init {
        val kord by inject<ShardManager>()
        kord.listener<ButtonInteractionEvent> { interaction ->
            if (!interaction.isFromGuild || !interaction.componentId.startsWith(ATTENDANCE_BTN_PREFIX)) return@listener
            handle(interaction)
        }
    }


    data class MessageUpdateInfo(
        var lastUpdate: Instant,
        val prevMessage: Message,
        val lastInteractionHook: InteractionHook,
        var newAttendees: MutableSet<UserSnowflake>,
        var lostAttendees: MutableSet<UserSnowflake>,
        var updater: Job? = null
    )
    // AttendanceID -> updateInfo
    val messageUpdateMap = ConcurrentHashMap<Long, MessageUpdateInfo>()

    private suspend fun handle(interaction: ButtonInteractionEvent) {
        val buttonId = interaction.button.id ?: return
        val guild = interaction.guild ?: return
        val member = interaction.member ?: return
        val guildId = guild.idLong
        val channelId = interaction.channel.idLong
        val messageId = interaction.messageIdLong
        if (!buttonId.startsWith(ATTENDANCE_BTN_PREFIX)) return
        if (buttonId == ATTENDANCE_BTN_PREFIX + ATTENDANCE_BTN_ATTEND) {
            val attendanceEntry = attendanceManager.getByGuildChannelMessageKey(guildId, channelId, messageId) ?: return
            val role = attendanceEntry.requiredRole?.let { guild.getRoleById(it) ?: return }
            if (role != null) {
                if (!member.hasRole(role)) return
            }
            var nextCloseTime = attendanceEntry.nextMoment
            attendanceEntry.closeOffset?.let {
                nextCloseTime -= it
            }
            val now = Clock.System.now()
            if (now < nextCloseTime) {
                attendeesManager.getById(attendanceEntry.attendanceId, member.idLong)?.run {
                    interaction.reply("You were already registered as attending for **${attendanceEntry.topic}**")
                        .setEphemeral(true).await()
                    return
                }

                attendeesManager.store(AttendeesData(attendanceEntry.attendanceId, interaction.user.idLong, now))

                // Give notify role if configured
                var extraInfo = ""
                attendanceEntry.notifyRoleId?.let {
                    val notifyRole = guild.getRoleById(it) ?: return@let
                    try {
                        guild.addRoleToMember(member, notifyRole).reason("attendance notify role").queue()
                    } catch (t: Exception) {
                        extraInfo = "Couldn't give you the notify role: ${t.message}"
                    }
                }
                val hook = interaction.reply("You are now registered as attending for **${attendanceEntry.topic}**\n" + extraInfo)
                    .setEphemeral(true).await()
                queueMessageUpdate(attendanceEntry, true, interaction.message, hook)
            }
        } else if (buttonId == ATTENDANCE_BTN_PREFIX + ATTENDANCE_BTN_REVOKE) {
            val attendanceEntry = attendanceManager.getByGuildChannelMessageKey(guildId, channelId, messageId) ?: return
            val attendeeEntry = attendeesManager.getById(attendanceEntry.attendanceId, member.idLong) ?: run {
                interaction.reply("You are not registered as attending for **${attendanceEntry.topic}**")
                    .setEphemeral(true).await()
                return
            }
            var nextCloseTime = attendanceEntry.nextMoment
            attendanceEntry.closeOffset?.let {
                nextCloseTime -= it
            }
            val now = Clock.System.now()
            if (now < nextCloseTime) {
                attendeesManager.delete(attendeeEntry)
                val hook =
                    interaction.reply("You are no longer registered as attending for **${attendanceEntry.topic}**")
                        .setEphemeral(true).await()
                queueMessageUpdate(attendanceEntry, false, interaction.message, hook)
            }
        }
    }

    private fun queueMessageUpdate(
        attendanceEntry: AttendanceData,
        new: Boolean,
        message: Message,
        hook: InteractionHook
    ) {
        val now = Clock.System.now()
        val oldEmbed = message.embeds.first()
        val user = hook.interaction.user
        val queueEntry = messageUpdateMap[attendanceEntry.attendanceId] ?: MessageUpdateInfo(
            Instant.DISTANT_PAST,
            message,
            hook,
            mutableSetOf(),
            mutableSetOf(),
            updaterJob(attendanceEntry.attendanceId)
        )
        if (new) queueEntry.newAttendees.add(user)
        else queueEntry.lostAttendees.add(user)

        val prevTime = queueEntry.lastUpdate
        if (prevTime < now - 5.seconds) {
            runInstantMessageUpdate(oldEmbed, queueEntry)

            // I hate this
            messageUpdateMap[attendanceEntry.attendanceId] =
                MessageUpdateInfo(now, message, hook, mutableSetOf(), mutableSetOf())
        } else {

            messageUpdateMap[attendanceEntry.attendanceId] =
                MessageUpdateInfo(now, message, hook, mutableSetOf<UserSnowflake>().apply {
                    addAll(queueEntry.newAttendees)
                    if (new) add(user)
                    else remove(user)

                }, mutableSetOf<UserSnowflake>().apply {
                    addAll(queueEntry.lostAttendees)
                    if (!new) add(user)
                    else remove(user)
                }, queueEntry.updater)
        }
    }

    private fun updaterJob(attendanceId: Long): Job = TaskScope.launch {
        delay(5.seconds)
        val now = Clock.System.now()
        val updateInfo = messageUpdateMap[attendanceId] ?: return@launch
        if (updateInfo.lastUpdate < now - 5.seconds) {
            messageUpdateMap[attendanceId] = updateInfo.apply {
                this.updater = updaterJob(attendanceId)
            }
            return@launch
        }
        if (updateInfo.newAttendees.isNotEmpty() || updateInfo.lostAttendees.isNotEmpty()) {
            runInstantMessageUpdate(updateInfo.prevMessage.embeds.first(), updateInfo)
            messageUpdateMap[attendanceId] = updateInfo.apply {
                this.newAttendees = mutableSetOf()
                this.lostAttendees = mutableSetOf()
                this.lastUpdate = Clock.System.now()
                this.updater = updaterJob(attendanceId)
            }
        } else {
            messageUpdateMap.remove(attendanceId)
        }
    }

    private fun runInstantMessageUpdate(
        oldEmbed: MessageEmbed,
        messageUpdateInfo: MessageUpdateInfo
    ) {
        val lastInteractionHook = messageUpdateInfo.lastInteractionHook

        lastInteractionHook.editMessageEmbedsById(
            messageUpdateInfo.prevMessage.idLong,
            InlineEmbed(oldEmbed).apply {
                val sb = StringBuilder(oldEmbed.description)

                // Regex("\n(?:<@id1>|<@id2>)")
                val regex = ("\n(?:" + messageUpdateInfo.lostAttendees.joinToString("|") {
                    Regex.escape(it.asMention)
                } + ")").toRegex()

                (messageUpdateInfo.newAttendees).forEach {
                    sb.append("\n")
                    sb.append(it.asMention)
                }

                val out = sb.toString()
                description =if (messageUpdateInfo.lostAttendees.isNotEmpty()) out.replace(regex, "")
                else out
            }.build()
        ).queue()
    }

    companion object {
        const val ATTENDANCE_BTN_ATTEND: String = "yes"
        const val ATTENDANCE_BTN_PREFIX = "attendance-"
        const val ATTENDANCE_BTN_REVOKE = "no"
    }

}
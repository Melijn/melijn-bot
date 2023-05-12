package me.melijn.bot.commands

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.toDuration
import com.kotlindiscord.kord.extensions.utils.waitFor
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.replyModal
import dev.minn.jda.ktx.messages.MessageCreate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.AttendanceManager
import me.melijn.bot.database.manager.AttendeesManager
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSubCommand
import me.melijn.bot.utils.embedWithColor
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.TimeFormat
import org.koin.core.component.inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes

@KordExtension
class AttendanceExtension : Extension() {

    override val name: String = "attendance"

    val attendanceManager by inject<AttendanceManager>()
    val attendeesManager by inject<AttendeesManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "attendance"
            description = "Manage attendance events"

            publicGuildSubCommand(::AttendanceCreateArgs) {
                name = "create"
                description = "Create a new attendance event"
                noDefer()

                action {
                    var topic = this.arguments.topic
                    var description = this.arguments.description
                    var schedule = this.arguments.schedule
                    var givenMoment = this.arguments.nextMoment

                    val zone = this.arguments.zoneId
                    val atZone = givenMoment.atZone(zone)
                    val ms = atZone.toEpochSecond() * 1000

                    val discordTimestamp = TimeFormat.DATE_TIME_LONG.format(givenMoment.atZone(zone))

                    this.event.replyModal("attendance-create-modal", "Create Attendance Event") {
                        this.short("topic", "Topic", true, topic)
                        this.paragraph("description", "Description", true, description)
                        this.short("schedule", "Schedule", false, schedule)
                        this.short("moment", "Moment", false, arguments.moment?.toString(), placeholder = "yyyy-MM-dd HH:mm")
                    }.queue()

                    val modalInteractionEvent = shardManager.waitFor<ModalInteractionEvent>(100.minutes) {
                       this.user.idLong == user.idLong && this.modalId == "attendance-create-modal"
                    } ?: bail("modal timeout ${user.asMention}")

                    topic = modalInteractionEvent.getValue("topic")!!.asString
                    description = modalInteractionEvent.getValue("description")?.asString
                    schedule = modalInteractionEvent.getValue("schedule")?.asString

                    // Recalc given moment from modal reply
                    val moment = modalInteractionEvent.getValue("moment")?.asString?.let { DateTimeConverter.parseFromString(it) }
                    givenMoment = nextMomentFromMomentOrSchedule(moment, schedule)

                    val textChannel = arguments.channel as TextChannel
                    val message = textChannel.sendMessage(MessageCreate {
                        embedWithColor {
                            title = topic
                            this.description = description
                            this.description += "\n\nAttendance for: $discordTimestamp"
                            this.timestamp = givenMoment
                        }
                        actionRow(Button.success("attendance-yes", "Attend"), Button.danger("attendance-no", "Revoke"))
                    }).await()

                    val nextMoment = Instant.ofEpochMilli(ms).toKotlinInstant()
                    val data = attendanceManager.genstore(
                        topic,
                        guild!!.idLong,
                        arguments.channel.idLong,
                        message.idLong,
                        arguments.notifyAttendees,
                        arguments.repeating,
                        nextMoment,
                        schedule,
                        description,
                        arguments.attendeesRole?.idLong,
                        arguments.closeOffset?.toDuration(TimeZone.UTC),
                    )

                    modalInteractionEvent.interaction.reply(MessageCreate {
                        content = "Next attendance is at: $atZone\n${discordTimestamp}\n${data}"
                    }).await()
                }
            }

            publicGuildSubCommand(::AttendanceRemoveArgs) {
                name = "remove"
                description = "Remove an attendance event"

                action {
                    val attendanceId = arguments.attendanceId
                    val removed = attendanceManager.delete(attendanceId, guild!!.idLong)

                    respond {
                        content = if (removed) {
                            "Removed attendance event with id: $attendanceId"
                        } else {
                            "Failed to remove attendance event with id: $attendanceId"
                        }
                    }
                }
            }

            publicGuildSubCommand {
                name = "list"
                description = "List the attendance events"

                action {
                    val attendanceEvents = attendanceManager.getByGuildKey(guild!!.idLong)

                    respond {
                        embedWithColor {
                            title = "Attendance Events"

                            description = "**id - next moment - topic**\n"
                            description += attendanceEvents.joinToString("\n") { attendance ->
                                val moment = attendance.nextMoment.toJavaInstant()
                                val discordTimeStamp = TimeFormat.DATE_TIME_LONG.format(moment.toEpochMilli())

                                "${attendance.attendanceId} - $discordTimeStamp - ${attendance.topic}"
                            }

                            if (attendanceEvents.isEmpty()) {
                                description = "There are no attendance events in this server"
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        val cronParser = CronParser(cronDefinition)
    }

    inner class AttendanceRemoveArgs : Arguments() {
        val attendanceId by long {
            name = "attendance-id"
            description = "The id of the attendance event"
        }
    }

    inner class AttendanceCreateArgs : Arguments() {
        val topic by string {
            name = "topic"
            description = "The topic of the attendance event"
        }
        val channel by channel {
            name = "channel"
            description = "The channel of the attendance event"
            requiredChannelTypes.add(ChannelType.TEXT)
        }
        val moment by optionalDateTime {
            name = "moment"
            description = "The moment of the attendance event, you can provide this or schedule"
        }
        val zoneId by defaultingZoneId {
            name = "time-zone"
            description = "Your timezone zoneId (e.g. Europe/Amsterdam, Europe/Brussels, UTC)"
            defaultValue = ZoneId.of("UTC")
        }
        val schedule by optionalString {
            name = "schedule"
            description = "The schedule of the attendance events"
        }
        val description by optionalString {
            name = "description"
            description = "The description of the attendance event"
        }
        val repeating by defaultingBoolean {
            name = "repeating"
            description = "Whether to plan the next attendance based on the schedule after the last one + closeOffset"
            defaultValue = false
        }
        val closeOffset by optionalDuration {
            name = "close-offset"
            description = "The close offset of the attendance event"
        }
        val notifyOffset by optionalDuration {
            name = "notify-offset"
            description = "The notify offset of the attendance event"
        }
        val scheduleTimeout by optionalDuration {
            name = "schedule-timeout"
            description = "The schedule timeout of the attendance event"
        }
        val notifyAttendees by defaultingBoolean {
            name = "notify-attendees"
            description = "Whether or not to notify attendees"
            defaultValue = true
        }
        val attendeesRole by optionalRole {
            name = "attendees-role"
            description = "The role given to attendees"
        }

        val nextMoment: LocalDateTime by lazy {
            nextMomentFromMomentOrSchedule(moment, schedule)
        }
    }

    private fun nextMomentFromMomentOrSchedule(moment: LocalDateTime?, schedule: String?) =
        if (moment == null && schedule == null) {
            bail("You must provide either a moment or a schedule")
        } else if (moment != null) {
            moment
        } else {
            val cron = cronParser.parse(schedule)
            val execTimes = ExecutionTime.forCron(cron)
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            val next = execTimes.nextExecution(now)
            next.getOrNull()?.toLocalDateTime() ?: bail("The schedule you provided is invalid")
        }
}
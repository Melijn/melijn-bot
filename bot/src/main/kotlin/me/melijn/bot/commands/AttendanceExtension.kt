package me.melijn.bot.commands

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.converters.builders.ValidationContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.waitFor
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.replyModal
import dev.minn.jda.ktx.messages.InlineEmbed
import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageCreate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.AttendanceManager
import me.melijn.bot.database.manager.AttendeesManager
import me.melijn.bot.database.model.AttendanceState
import me.melijn.bot.services.AttendanceService
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.KordExUtils
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSubCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.bot.utils.embedWithColor
import me.melijn.gen.AttendanceData
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.TimeFormat
import net.dv8tion.jda.api.utils.messages.MessageEditData
import net.dv8tion.jda.internal.utils.Helpers
import org.koin.core.component.inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import me.melijn.bot.events.buttons.AttendanceButtonHandler.Companion.ATTENDANCE_BTN_ATTEND as BTN_ATTEND_SUFFIX
import me.melijn.bot.events.buttons.AttendanceButtonHandler.Companion.ATTENDANCE_BTN_PREFIX as BTN_PREFIX
import me.melijn.bot.events.buttons.AttendanceButtonHandler.Companion.ATTENDANCE_BTN_REVOKE as BTN_REVOKE_SUFFIX

@KordExtension
class AttendanceExtension : Extension() {

    override val name: String = "attendance"

    val attendanceManager by inject<AttendanceManager>()
    val attendeesManager by inject<AttendeesManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "attendance"
            description = "Manage attendance events"

            requirePermission(Permission.ADMINISTRATOR)


            publicGuildSubCommand(::AttendanceCreateArgs) {
                name = "create"
                description = "Create a new attendance event"

                requirePermission(Permission.ADMINISTRATOR)
                noDefer()

                action {
                    val guild = guild!!
                    var topic = this.arguments.topic
                    var description = this.arguments.description
                    var schedule = this.arguments.schedule
                    var givenMoment = this.arguments.nextMoment

                    val zone = this.arguments.zoneId
                    val atZone = givenMoment.atZone(zone)
                    val ms = atZone.toEpochSecond() * 1000

                    this.event.replyModal("attendance-create-modal", "Create Attendance Event") {
                        this.short("topic", "Topic", true, topic, placeholder = "Title or topic for the event")
                        this.paragraph(
                            "description",
                            "Description",
                            true,
                            description,
                            placeholder = "What is the event about ?"
                        )
                        this.short(
                            "schedule",
                            "Schedule [QUARTZ CRON format]",
                            false,
                            schedule,
                            placeholder = "0 15 10 ? * 6L 2022-2025"
                        )
                        this.short(
                            "moment",
                            "Moment",
                            false,
                            arguments.moment?.toString(),
                            placeholder = "yyyy-MM-dd HH:mm"
                        )
                    }.await()

                    val modalInteractionEvent = shardManager.waitFor<ModalInteractionEvent>(100.minutes) {
                        this.user.idLong == user.idLong && this.modalId == "attendance-create-modal"
                    } ?: bail("modal timeout ${user.asMention}")

                    topic = modalInteractionEvent.getValue("topic")!!.asString
                    description = modalInteractionEvent.getValue("description")?.asString
                    schedule = modalInteractionEvent.getValue("schedule")?.asString

                    // Recalc given moment from modal reply
                    val moment =
                        modalInteractionEvent.getValue("moment")?.asString?.let { DateTimeConverter.parseFromString(it) }
                    givenMoment = nextMomentFromMomentOrSchedule(moment, schedule)

                    val textChannel = arguments.channel
                    val message = textChannel.sendMessage(
                        getAttendanceMessage(
                            topic,
                            description,
                            givenMoment,
                            zone
                        )
                    ).await()

                    val nextMoment = Instant.ofEpochMilli(ms).toKotlinInstant()

                    val closeOffset = arguments.closeOffset
                    val notifyOffset = arguments.notifyOffset

                    val maxOffset = maxOf(closeOffset ?: ZERO, notifyOffset ?: ZERO)

                    val notifyRoleId = arguments.notifyRole?.idLong?.let {
                        val role = guild.getRoleById(it) ?: return@let null
                        guild.createCopyOfRole(role).reason("attendance notify role creation").await().idLong
                    }

                    val data = attendanceManager.insertAndGetRow(
                        guild.idLong,
                        arguments.channel.idLong,
                        message.idLong,
                        arguments.requiredRole?.idLong,
                        notifyRoleId,
                        closeOffset,
                        arguments.notifyAttendees,
                        notifyOffset,
                        topic,
                        description,
                        arguments.repeating,
                        nextMoment,
                        AttendanceState.LISTENING,
                        nextMoment - maxOffset,
                        schedule,
                        zone.toString(),
                        arguments.scheduleTimeout
                    )

                    val service by inject<AttendanceService>()
                    service.waitingJob.cancel(CancellationException("new attendance, recheck first"))

                    modalInteractionEvent.interaction.reply(MessageCreate {
                        embedWithColor {
                            this.title = tr("attendance.created")
                            this.description = getAttendanceInfoMessage(data, message.jumpUrl)
                        }
                    }).await()
                }
            }

            publicGuildSubCommand(::AttendanceRefArgs) {
                name = "edit"
                description = "Edits an existing attendance event"

                requirePermission(Permission.ADMINISTRATOR)
                noDefer()

                action {
                    val guild = guild!!

                    val components = StringSelectMenu("attendance-edit-selector", options = buildList {
                        add(
                            SelectOption(
                                "General",
                                "general",
                                "Topic, description",
                                Emoji.fromUnicode("\uD83E\uDEA7")
                            )
                        )
                        add(
                            SelectOption(
                                "Schedule",
                                "schedule",
                                "Schedule, next-moment, repeating, close- and reopen-time",
                                Emoji.fromUnicode("\uD83D\uDDD3")
                            )
                        )
                        add(
                            SelectOption(
                                "Notification",
                                "notification",
                                "Notification settings",
                                Emoji.fromUnicode("\uD83D\uDD14")
                            )
                        )
                        add(
                            SelectOption(
                                "Misc",
                                "misc",
                                "Required role, state, timezone",
                                Emoji.fromUnicode("\uD83D\uDD27")
                            )
                        )
                    })
                    val selector = this.event.reply(MessageCreate {
                        content = "Select a category to edit"
                        actionRow(components)
                    }).setEphemeral(true).await()


                    val selectEvent = shardManager.waitFor<StringSelectInteractionEvent>(15.minutes) {
                        this.user.idLong == user.idLong && this.componentId == "attendance-edit-selector"
                    } ?: bail("modal timeout ${user.asMention}")
                    selector.editOriginalComponents(ActionRow.of(components.withDisabled(true))).queue()

                    val selected = selectEvent.values.first()
                    selectEvent.reply("You selected $selected, L!").queue()
                }
            }

            publicGuildSubCommand(::AttendanceRemoveArgs) {
                name = "remove"
                description = "Remove an attendance event"
                requirePermission(Permission.ADMINISTRATOR)

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
                requirePermission(Permission.ADMINISTRATOR)

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

            publicGuildSubCommand(::AttendanceRefArgs) {
                name = "info"
                description = "Display all information of an attendance event"

                requirePermission(Permission.ADMINISTRATOR)


                action {
                    val data = this.arguments.attendanceData.await()
                    val jumpUrl = Helpers.format(
                        Message.JUMP_URL,
                        data.guildId,
                        data.channelId,
                        data.messageId
                    )

                    respond {
                        embedWithColor {
                            title = tr("attendance.infoTitle")
                            description = getAttendanceInfoMessage(data, jumpUrl)
                        }
                    }
                }
            }
        }
    }

    context(CommandContext)
    private suspend fun getAttendanceInfoMessage(data: AttendanceData, jumpUrl: String): String {
        val nextMomentTimestamp = TimeFormat.DATE_TIME_LONG.format(data.nextMoment.toJavaInstant())
        val nextStateChangeMomentTimestamp =
            TimeFormat.DATE_TIME_LONG.format(data.nextStateChangeMoment.toJavaInstant())

        return tr("attendance.info",
            data.topic,
            data.description ?: "",
            jumpUrl,
            nextMomentTimestamp,
            data.notifyOffset?.formatElapsed() ?: "unset",
            data.notifyRoleId?.let { "<@&$it>" } ?: "unset",
            data.closeOffset?.formatElapsed() ?: "unset",
            data.scheduleTimeout?.formatElapsed() ?: "unset",
            nextStateChangeMomentTimestamp,
            data.state.toString(),
            data.schedule ?: "unset",
            data.attendanceId
        )
    }

    companion object {
        private val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        private val cronParser = CronParser(cronDefinition)

        fun getAttendanceMessage(
            topic: String,
            description: String?,
            givenMoment: LocalDateTime,
            timeZone: ZoneId
        ) = MessageCreate {
            val discordTimestamp = TimeFormat.DATE_TIME_LONG.format(givenMoment.atZone(timeZone))
            val discordReltime = TimeFormat.RELATIVE.format(givenMoment.atZone(timeZone))
            val translations: TranslationsProvider by inject()
            embed {
                title = topic
                this.description = translations.tr(
                    "attendance.messageLayout.active", Locale.getDefault(),
                    description,
                    discordTimestamp,
                    discordReltime,
                    ""
                )
            }
            actionRow(
                Button.success(BTN_PREFIX + BTN_ATTEND_SUFFIX, "Attend"),
                Button.danger(BTN_PREFIX + BTN_REVOKE_SUFFIX, "Revoke")
            )
        }

        context(InlineMessage<MessageEditData>, InlineEmbed)
        fun applyFinishedMessage(
            topic: String,
            description: String?,
            attendees: String,
            givenMoment: Instant
        ) {
            val discordTimestamp = TimeFormat.DATE_TIME_LONG.format(givenMoment)
            val translations: TranslationsProvider by inject()

            this@InlineEmbed.title = "[Finished] $topic"
            this@InlineEmbed.description = translations.tr(
                "attendance.messageLayout.finished", Locale.getDefault(),
                description,
                discordTimestamp,
                attendees
            )
            this@InlineMessage.builder.setComponents(emptySet())
        }

        fun nextMomentFromCronSchedule(schedule: String): LocalDateTime? {
            val cron = cronParser.parse(schedule)
            val execTimes = ExecutionTime.forCron(cron)
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            val next = execTimes.nextExecution(now)
            return next.getOrNull()?.toLocalDateTime()
        }
    }

    val jumpUrlRegex = Message.JUMP_URL_PATTERN.toRegex()

    inner class AttendanceRefArgs : Arguments() {
        private var isId = false
        private lateinit var locale: Locale

        private val attendanceId by string {
            name = "attendance-id"
            description = "The id of the attendance event OR message-reference-url"

            this.validate {
                locale = this.context.resolvedLocale.await()
                val id = this.value.toLongOrNull()
                val guildId: Long = this@validate.context.guild?.idLong!!
                isId = id != null
                if (isId) {
                    this.failIf {
                        attendanceManager.getByAttendanceKey(id!!)?.guildId != guildId
                    }
                } else if (jumpUrlRegex.matches(value)) {
                    failIf {
                        jumpUrlRegex.find(value)?.groups?.get("guild")?.value?.toLong() != guildId
                    }
                } else {
                    this.fail(tr("attendance.suppliedInvalidIdOrMsgUrl"))
                }
                this.pass()
            }
        }

        val attendanceData = TaskScope.async(
            TaskScope.dispatcher, start = CoroutineStart.LAZY
        ) {
            val translations: TranslationsProvider by inject()

            return@async if (isId)
                attendanceManager.getByAttendanceKey(attendanceId.toLong())
            else {
                fun regexGroupBail(): Nothing = bail("broken jda regex: Message.JUMP_URL_PATTERN")

                val res = jumpUrlRegex.find(attendanceId) ?: bail("you broke regex!")

                val guildId = res.groups["guild"]?.value?.toLong() ?: regexGroupBail()
                val channelId = res.groups["channel"]?.value?.toLong() ?: regexGroupBail()
                val messageId = res.groups["message"]?.value?.toLong() ?: regexGroupBail()

                attendanceManager.getById(guildId, channelId, messageId)
            } ?: bail(translations.tr("attendance.suppliedInvalidIdOrMsgUrl", locale))
        }
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
        val channel by channel<TextChannel> {
            name = "channel"
            description = "The channel of the attendance event"
            requireChannelType(ChannelType.TEXT)
            requirePermissions(Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_SEND)
        }

        val moment by optionalDateTime {
            name = "moment"
            description = "The next-moment of the attendance event, you must provide this or schedule"

            KordExUtils.addDateTimeAutocompletion()
        }

        val zoneId by defaultingZoneId {
            name = "time-zone"
            description = "Your timezone zoneId (e.g. Europe/Amsterdam, Europe/Brussels, UTC)"
            defaultValue = ZoneId.of("UTC")
        }
        val schedule by optionalString {
            name = "schedule"
            description = "The (quartz-cron) schedule of the attendance events"
        }
        val description by optionalString {
            name = "description"
            description = "The description of the attendance event"
        }
        val repeating by defaultingBoolean {
            name = "repeating"
            description = "Whether to plan the next attendance based on the schedule after the last one + closeOffset"
            defaultValue = true
        }
        val closeOffset by optionalDuration {
            name = "close-offset"
            description = "The close offset of the attendance event"
            validate {
                requireMinDuration(5.minutes)
            }
        }
        val notifyOffset by optionalDuration {
            name = "notify-offset"
            description = "The notify offset of the attendance event"
            validate {
                requireMinDuration(10.seconds)
            }
        }
        val scheduleTimeout by defaultingDuration {
            name = "schedule-timeout"
            description = "The schedule timeout of the attendance event"
            defaultValue = 10.seconds
            validate {
                requireMinDuration(10.seconds)
            }
        }
        val notifyAttendees by defaultingBoolean {
            name = "notify-attendees"
            description = "Whether or not to notify attendees"
            defaultValue = true
        }
        val notifyRole by optionalRole {
            name = "notify-role"
            description = "The role template to notify attendees with (will be cloned)"
            validate {
                if (value != null) {
                    failIf { context.guild?.selfMember?.canInteract(value!!) == false }
                    failIf { context.guild?.selfMember?.hasPermission(Permission.MANAGE_ROLES) == false }
                }
            }
        }
        val requiredRole by optionalRole {
            name = "required-role"
            description = "Attendees will need this role before being able to attend"
        }

        val nextMoment: LocalDateTime by lazy {
            nextMomentFromMomentOrSchedule(moment, schedule)
        }
    }

    private suspend fun ValidationContext<Duration?>.requireMinDuration(min: Duration) {
        failIf(tr("duration.minimalValue", min)) {
            value != null && value!! < min
        }
    }

    private fun nextMomentFromMomentOrSchedule(moment: LocalDateTime?, schedule: String?): LocalDateTime =
        if (moment == null && schedule == null) {
            bail("You must provide either a moment or a schedule")
        } else if (moment != null) {
            moment
        } else {
            requireNotNull(schedule) // compiler can't infer this is true sadly
            fun invalidCron(): Nothing =
                bail("The schedule you provided is invalid, it must be a quartz-cron job format")
            try {
                val next = nextMomentFromCronSchedule(schedule)
                next ?: invalidCron()
            } catch (e: Exception) {
                invalidCron()
            }
        }
}
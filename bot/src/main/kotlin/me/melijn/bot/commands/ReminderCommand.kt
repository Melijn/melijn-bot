package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalDuration
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalZonedDateTime
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.types.editingPaginator
import com.kotlindiscord.kord.extensions.types.respond
import kotlinx.datetime.Clock
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.ReminderManager
import me.melijn.bot.services.ReminderService
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.intRanges
import me.melijn.gen.RemindersData
import me.melijn.kordkommons.utils.escapeCodeBlock
import org.koin.core.component.inject
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.minutes

@KordExtension
class ReminderCommand : Extension() {
    override val name: String = "reminder"

    val reminderManager by inject<ReminderManager>()
    val service by inject<ReminderService>()

    override suspend fun setup() {
        publicSlashCommand {
            name = "reminder"
            description = "Manages reminders"

            ephemeralSubCommand(::ReminderAddArgs) {
                name = "add"
                description = "Creates a reminder"

                action {
                    val data = RemindersData(
                        user.idLong,
                        Clock.System.now() + arguments.remindAt,
                        arguments.text
                    )
                    reminderManager.store(data)
                    service.waitingJob.cancel(CancellationException("new reminder state, recheck first"))

                    respond {
                        val timeStamp = data.moment.toDiscord(TimestampType.LongDateTime)
                        val text = data.reminderText.escapeCodeBlock()
                        content = tr("reminders.added", timeStamp, text)
                    }
                }
            }

            ephemeralSubCommand(::ReminderRemoveArgs) {
                name = "remove"
                description = "Removes a reminder"

                action {
                    val reminders = reminderManager.getRemindersSorted(user)
                    if (reminders.isEmpty()) {
                        respond {
                            content = tr("reminders.list.empty")
                        }
                        return@action
                    }

                    val toRemove = arguments.indices.list.flatten().mapNotNull { reminders.getOrNull(it - 1) }.toSet()

                    reminderManager.bulkDelete(toRemove)
                    service.waitingJob.cancel(CancellationException("new reminder state, recheck first"))

                    respond {
                        content = tr("reminders.removed", toRemove.size)
                    }
                }
            }

            ephemeralSubCommand {
                name = "list"
                description = "Shows all your reminders"

                action {
                    val reminders = reminderManager.getRemindersSorted(user)
                    if (reminders.isEmpty()) {
                        respond {
                            content = tr("reminders.list.empty")
                        }
                        return@action
                    }

                    val paginator = editingPaginator {
                        var i = 0
                        for (reminderChunk in reminders.chunked(10)) {
                            this.page {
                                title = "Reminders"
                                description = "`id. timestamp reminderText`\n"
                                for (reminder in reminderChunk) {
                                    val timeStamp = reminder.moment.toDiscord(TimestampType.LongDateTime)
                                    val shortenedText = reminder.reminderText.take(64)
                                    description += "$i. $timeStamp $shortenedText\n"
                                    i++
                                }
                            }
                        }
                    }
                    paginator.send()
                }
            }
        }
    }

    internal class ReminderAddArgs : Arguments() {
        val text by string {
            name = "message"
            description = "What the reminder should say"
        }
        val moment by optionalZonedDateTime {
            name = "moment"
            description =
                "Zoned Date Time (e.g. 2007-12-03T10:15:30+01:00 Europe/Paris) when the reminder should be sent"
            validate {
                failIf(tr("reminders.tooSoon")) {
                    this.value?.let {
                        ChronoUnit.MINUTES.between(it, ZonedDateTime.now()) >= 1
                    } ?: false
                }
            }
        }
        val duration by optionalDuration {
            name = "duration"
            description = "The duration between the reminder being sent and now"
            validate {
                atLeast(name, 1.minutes)
            }
        }
        val remindAt by lazy {
            this.moment?.let {
                ChronoUnit.MINUTES.between(it, ZonedDateTime.now()).minutes
            } ?: this.duration ?: bail("You must supply moment or duration as arguments")
        }
    }

    inner class ReminderRemoveArgs : Arguments() {
        val indices by intRanges {
            name = "ids"
            description = "id or ids to delete, can be in range format, separate them by comma"
            validate {
                val countLimit = 100
                failIf(tr("intRanges.providedTooMany", countLimit)) { this.value.list.size > countLimit }
                val reminderCount = reminderManager.getByUserIndex(this.context.user.idLong).size
                failIf(tr("reminders.outOfBounds", reminderCount)) {
                    !this.value.list.all { it.first >= 1 && it.last <= reminderCount }
                }
            }
        }
    }
}
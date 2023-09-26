package me.melijn.bot.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.MissingUserManager
import me.melijn.bot.database.manager.ReminderManager
import me.melijn.bot.database.manager.openPrivateChannelSafely
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.Log
import me.melijn.gen.RemindersData
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration


@Inject(true, 1)
class ReminderService {

    private val reminderManager by KoinUtil.inject<ReminderManager>()
    private val missingUserManager by KoinUtil.inject<MissingUserManager>()
    private val shardManager by KoinUtil.inject<ShardManager>()
    private val logger by Log

    /** it's the job of [ReminderCommand] to cancel [waitingJob] */
    var waitingJob = TaskScope.launch { }

    init {
        pollingLoop()
    }

    private fun pollingLoop() = TaskScope.launch {
        while (true) {
            val reminderEntry = reminderManager.getNextUpcomingReminder()
            if (reminderEntry == null) {
                // The waitingJob will be cancelled interrupted by the remindersCommand so it can retry,
                // otherwise this coroutine will delay infinitely and be idle
                waitingJob = launch { delay(Duration.INFINITE) }
                waitingJob.join()
                continue
            }

            val now = Clock.System.now()

            if (reminderEntry.moment <= now) {
                try {
                    doUpdates(reminderEntry)
                } catch (t: Exception) {
                    logger.error(t) { "error while processing reminder for: ${reminderEntry.userId}" }
                }
                reminderManager.delete(reminderEntry)
            }

            waitingJob = launch {
                if (reminderEntry.moment > now) {
                    val duration = reminderEntry.moment - now
                    logger.info { "Next reminder in: $duration" }
                    delay(duration)
                }
            }
            waitingJob.join()
        }
    }

    private suspend fun doUpdates(reminderEntry: RemindersData) {
        val privateChannel = shardManager.openPrivateChannelSafely(reminderEntry.userId) ?: return
        val reminderText = reminderEntry.reminderText
        try {
            privateChannel.sendMessage("Reminder:\n$reminderText").awaitOrNull()
        } catch (e: ErrorResponseException) {
            val retry: suspend (String) -> Message? = { cause: String ->
                // TODO: Add user localization api and storage for outside of commands
                val encoder = java.util.Base64.getEncoder()
                privateChannel.sendMessage(
                    "Your reminder was blocked by ${cause}, here's the base64 text:\n${
                        encoder.encode(reminderText.toByteArray())
                    }"
                ).awaitOrNull()
            }
            when (e.errorResponse) {
                ErrorResponse.UNKNOWN_CHANNEL -> {
                    logger.warn { "Unknown channel, right after opening it lololol" }
                }

                ErrorResponse.CANNOT_SEND_TO_USER -> {
                    logger.warn { "User was stinky, no longer shares a server with us, we now mark them as dms closed: ${reminderEntry.userId}" }
                    missingUserManager.markUserDmsClosed(reminderEntry.userId)
                }

                ErrorResponse.MESSAGE_BLOCKED_BY_AUTOMOD -> retry("automod")
                ErrorResponse.MESSAGE_BLOCKED_BY_HARMFUL_LINK_FILTER -> retry("harmful link filter")

                else -> {
                    logger.error(e) { "Failed due to unhandled cause" }
                }
            }
        }
    }
}
package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import dev.kord.core.event.message.MessageCreateEvent
import me.melijn.bot.Settings
import org.koin.core.component.inject

object KordExUtils {

    suspend fun CheckContext<MessageCreateEvent>.userIsOwner() {
        val botSettings by inject<Settings>()
        failIfNot("bot owner command") {
            botSettings.bot.ownerIds.split(",").any {
                it.trim() == this.event.message.author?.id?.value.toString()
            }
        }
    }
}
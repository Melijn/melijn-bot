package me.melijn.bot.events

import dev.kord.core.Kord
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.on
import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.KoinUtil.inject

@Inject(true)
class ButtonInteractionListener {

    init {
        val kord by inject<Kord>()
        kord.on<ButtonInteractionCreateEvent> {
            if (interaction.componentId == "DESTROYY") {
                interaction.message.delete("DESTROYY latex")
            }
        }
    }

}
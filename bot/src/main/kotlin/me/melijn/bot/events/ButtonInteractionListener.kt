package me.melijn.bot.events

import dev.kord.core.Kord
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.on
import me.melijn.ap.injector.Inject
import me.melijn.bot.cache.ButtonCache
import me.melijn.bot.model.AbstractOwnedMessage
import me.melijn.bot.utils.KoinUtil.inject

const val LATEX_DESTROY_BUTTON_ID = "LATEX-DESTROY"

@Inject(true)
class ButtonInteractionListener {

    private val buttonCache by inject<ButtonCache>()

    init {
        val kord by inject<Kord>()
        kord.on<ButtonInteractionCreateEvent> {
            val ownedMsg = AbstractOwnedMessage.from(interaction)
            if (interaction.componentId == LATEX_DESTROY_BUTTON_ID && buttonCache.latexButtonOwners[ownedMsg] == true) {
                interaction.message.delete("(latex destroy button) ${interaction.user.tag}")
            }
        }
    }
}
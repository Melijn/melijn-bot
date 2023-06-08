package me.melijn.bot.events.buttons

import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.utils.getLocale
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.MessageEdit
import me.melijn.bot.cache.SearchPlayMenuCache
import me.melijn.bot.model.OwnedGuildMessage
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

object SPlayMenuButtonHandler {

    const val SPLAY_BTN_ID_PREFIX = "SPLAY-"
    const val SPLAY_BTN_CANCEL = "CANCEL"
    private val searchPlayMenuCache by inject<SearchPlayMenuCache>()
    private val translationsProvider by inject<TranslationsProvider>()

    suspend fun handle(event: ButtonInteractionEvent) {
        val interaction = event.interaction
        val ownedGuildMessage = OwnedGuildMessage.from(interaction)
        val searchPlayMenu = searchPlayMenuCache.cache[ownedGuildMessage] ?: return

        val guild = event.interaction.guild!!
        val locale = event.getLocale()

        val title: String
        val description: String
        when (val option = interaction.componentId.drop(SPLAY_BTN_ID_PREFIX.length)) {
            SPLAY_BTN_CANCEL -> {
                title = translationsProvider.tr("splay.cancelledTitle", locale)
                description = translationsProvider.tr("splay.cancelledDesc", locale)
            }
            else -> {
                val trackId = option.toIntOrNull() ?: return
                val selected = searchPlayMenu.options.getOrNull(trackId) ?: return
                val trackManager = guild.getTrackManager()

                trackManager.queue(selected, searchPlayMenu.queuePosition)

                title = translationsProvider.tr("play.title", locale, event.interaction.user.asTag)
                description = translationsProvider.tr(
                    "play.addedOne",
                    locale,
                    trackManager.queue.size,
                    selected.url,
                    selected.title,
                    selected.length.formatElapsed()
                )
            }
        }

        searchPlayMenuCache.cache.remove(ownedGuildMessage)
        event.interaction.editMessage(MessageEdit {
            embed {
                this.title = title
                this.description = description
            }
        }).await()
    }
}
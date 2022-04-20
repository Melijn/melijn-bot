package me.melijn.bot.events.buttons

import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.utils.getLocale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.updatePublicMessage
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.message.create.embed
import me.melijn.ap.injector.Inject
import me.melijn.bot.cache.SearchPlayMenuCache
import me.melijn.bot.model.OwnedGuildMessage
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsed

@Inject(true)
class SPlayMenuButtonHandler {

    private val searchPlayMenuCache by inject<SearchPlayMenuCache>()
    private val translationsProvider by inject<TranslationsProvider>()

    init {
        val kord by inject<Kord>()
        kord.on<GuildButtonInteractionCreateEvent> {
            if (!interaction.componentId.startsWith(splayBtnIdPrefix)) return@on
            handle(this)
        }
    }

    companion object {
        val splayBtnIdPrefix = "splay-"
        val splayBtnCancel = "cancel"
    }

    private suspend fun handle(event: GuildButtonInteractionCreateEvent) {
        val interaction = event.interaction
        val ownedGuildMessage = OwnedGuildMessage.from(interaction)
        val searchPlayMenu = searchPlayMenuCache.cache[ownedGuildMessage] ?: return

        val guild = event.interaction.getGuild()
        val locale = event.getLocale()

        val title: String
        val description: String
        when (val option = interaction.componentId.drop(splayBtnIdPrefix.length)) {
            splayBtnCancel -> {
                title = translationsProvider.tr("splay.cancelledTitle", locale)
                description = translationsProvider.tr("splay.cancelledDesc", locale)
            }
            else -> {
                val trackId = option.toIntOrNull() ?: return
                val selected = searchPlayMenu.options.getOrNull(trackId) ?: return
                val trackManager = guild.getTrackManager()

                trackManager.queue(selected, searchPlayMenu.queuePosition)

                title = translationsProvider.tr("play.title", locale, event.interaction.user.tag)
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
        event.interaction.updatePublicMessage {
            embed {
                this.title = title
                this.description = description
            }
        }
    }
}
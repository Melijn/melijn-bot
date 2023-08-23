package me.melijn.bot.events.buttons

import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.bot.cache.ButtonCache
import me.melijn.bot.commands.games.TicTacToeExtension
import me.melijn.bot.database.manager.TicTacToeManager
import me.melijn.bot.events.buttons.AttendanceButtonHandler.ATTENDANCE_BTN_ATTEND
import me.melijn.bot.events.buttons.AttendanceButtonHandler.ATTENDANCE_BTN_PREFIX
import me.melijn.bot.events.buttons.AttendanceButtonHandler.ATTENDANCE_BTN_REVOKE
import me.melijn.bot.model.AbstractOwnedMessage
import me.melijn.bot.utils.KoinUtil.inject
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.sharding.ShardManager

const val LATEX_DESTROY_BUTTON_ID = "LATEX-DESTROY"
const val TICTACTOE_DENY_BUTTON_ID = "TTT-DENY"
const val TICTACTOE_ACCEPT_BUTTON_ID = "TTT-ACCEPT"
const val TICTACTOE_ACTION_PREFIX_ID = "TTT-ACTION-"

@Inject(true)
class ButtonInteractionListener {

    private val buttonCache by inject<ButtonCache>()

    init {
        val kord by inject<ShardManager>()
        kord.listener<ButtonInteractionEvent> {interaction ->
            val ownedMsg = AbstractOwnedMessage.from(interaction)
            if (interaction.componentId == LATEX_DESTROY_BUTTON_ID && buttonCache.latexButtonOwners[ownedMsg] == true) {
                interaction.message.delete()
                    .reason("(latex destroy button) ${interaction.user.effectiveName}")
                    .queue()

            } else if (
                interaction.componentId == TICTACTOE_ACCEPT_BUTTON_ID ||
                interaction.componentId == TICTACTOE_DENY_BUTTON_ID ||
                interaction.componentId.startsWith(TICTACTOE_ACTION_PREFIX_ID)
            ) {
                val gameManager by inject<TicTacToeManager>()
                val game = gameManager.getGameByUser(interaction.user)

                if (game != null) {
                    TicTacToeExtension.handleButton(gameManager, game, interaction)
                }

            } else if (interaction.isFromGuild) {

                /** Guild interaction handlers */
                if (
                    interaction.componentId == ATTENDANCE_BTN_PREFIX + ATTENDANCE_BTN_ATTEND ||
                    interaction.componentId == ATTENDANCE_BTN_PREFIX + ATTENDANCE_BTN_REVOKE
                ) {
                    AttendanceButtonHandler.handle(interaction)

                } else if (
                    interaction.componentId.startsWith(SPlayMenuButtonHandler.SPLAY_BTN_ID_PREFIX)
                ) {
                    SPlayMenuButtonHandler.handle(interaction)

                }

            }
        }
    }
}
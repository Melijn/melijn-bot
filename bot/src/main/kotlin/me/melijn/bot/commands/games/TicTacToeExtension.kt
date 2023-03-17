package me.melijn.bot.commands.games

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import kotlinx.datetime.Clock
import me.melijn.bot.database.manager.BalanceManager
import me.melijn.bot.database.manager.TicTacToeManager
import me.melijn.bot.events.TICTACTOE_ACCEPT_BUTTON_ID
import me.melijn.bot.events.TICTACTOE_ACTION_PREFIX_ID
import me.melijn.bot.events.TICTACTOE_DENY_BUTTON_ID
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.KordExUtils.optionalAvailableCurrency
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.embedWithColor
import me.melijn.gen.TicTacToeData
import me.melijn.gen.TicTacToePlayerData
import org.postgresql.util.GT.tr
import kotlin.random.Random

//@KordExtension
class TicTacToeExtension : Extension() {

    override val name: String = "tic-tac-toe"
    val gameManager by KoinUtil.inject<TicTacToeManager>()

    companion object {
        suspend fun handleButton(gameManager: TicTacToeManager, game: TicTacToeData, interaction: ButtonInteraction) {
            val players = gameManager.ticTacToePlayerManager.getByIndex1(game.gameId)
            val activeUser = players.first { it.isUser1 == game.is_user1_turn }
            val waitingUser = players.firstOrNull { it.isUser1 != game.is_user1_turn }
            if (players.none { it.userId == interaction.user.id.value }) return

            if (interaction.componentId.startsWith(TICTACTOE_ACTION_PREFIX_ID)) { // GAME ACTION
                // check if user that clicked is active player
                if (activeUser.userId != interaction.user.id.value) return

                val board = parseBoard(game.board).toMutableList()
                val location = interaction.componentId.removePrefix(TICTACTOE_ACTION_PREFIX_ID).toInt()

                // update board
                board[location] = if (game.is_user1_turn) TTTState.O
                else TTTState.X
                game.is_user1_turn = !game.is_user1_turn
                game.board = serializeBoard(board)

                // check if game is won
                if (isGameDone(board)) {
                    handleGameDone(interaction, gameManager, game, interaction.user, waitingUser, isGameWon(board))
                    return
                }

                if (waitingUser == null) { // check if bot should now play
                    val options = board.count { it == TTTState.EMPTY }

                    // play random move
                    val randomLoc = Random.nextInt(options)
                    var idx = -1
                    for ((actualId, state) in board.withIndex()) {
                        if (state == TTTState.EMPTY) idx++
                        if (idx == randomLoc) {
                            board[actualId] = TTTState.X
                            break
                        }
                    }

                    game.is_user1_turn = !game.is_user1_turn
                    game.board = serializeBoard(board)

                    // check if game is done
                    if (isGameDone(board)) {
                        handleGameDone(interaction, gameManager, game, interaction.user, null, isGameWon(board))
                        return
                    }
                }

                game.last_played = Clock.System.now()
                gameManager.updateGame(game)

                interaction.deferPublicMessageUpdate().edit {
                    if (waitingUser == null) showGameState(game, interaction.user.id.value, TTTState.O)
                    else showGameState(game, waitingUser.userId, if (game.is_user1_turn) TTTState.O else TTTState.X)
                }

            } else if (interaction.componentId == TICTACTOE_ACCEPT_BUTTON_ID) { // START OF THE GAME
                val balanceManager by KoinUtil.inject<BalanceManager>()
                if (players.size < 2 || interaction.user.id.value != players[1].userId) return
                val p1 = players[0]

                balanceManager.min(interaction.user.id, game.bet * 2)
                balanceManager.min(Snowflake(p1.userId), game.bet)

                interaction.deferPublicMessageUpdate().edit {
                    showGameState(game, p1.userId, TTTState.O)
                }
            } else if (interaction.componentId == TICTACTOE_DENY_BUTTON_ID) {
                interaction.deferPublicMessageUpdate().edit {
                    embeds = mutableListOf()
                    components = mutableListOf()
                    content = when (interaction.user.id.value) {
                        waitingUser?.userId -> "${interaction.user.mention} denied <@${activeUser.userId}>'s tic-tac-toe invite."
                        activeUser.userId -> "${interaction.user.mention} cancelled their tic-tac-toe invite."
                        else -> return
                    }
                }
                gameManager.delete(game)
            }
        }

        private fun isGameDone(game: List<TTTState>): Boolean = game.all { it != TTTState.EMPTY } || isGameWon(game)

        fun isGameWon(game: List<TTTState>): Boolean {
            var result = false
            for (i in 0 until 3) {
                val column = game.filterIndexed { index, _ -> index % 3 == i }
                val row = game.filterIndexed { index, _ -> i * 3 <= index && index < i * 3 + 3 }
                result = result || column.allSame(TTTState.EMPTY) || row.allSame(TTTState.EMPTY)
            }
            result = result || (game[0] != TTTState.EMPTY && game[0] == game[4] && game[4] == game[8])
            result = result || (game[2] != TTTState.EMPTY && game[2] == game[4] && game[4] == game[6])
            return result
        }

        /**
         * Gives the game winner their mel, changes the game message to display who won, and deletes the game.
         * **/
        private suspend fun handleGameDone(
            interaction: ButtonInteraction,
            gameManager: TicTacToeManager,
            game: TicTacToeData,
            user: User,
            waitingUser: TicTacToePlayerData?,
            won: Boolean
        ) {
            suspend fun updateGameMessage(content: String) = interaction.deferPublicMessageUpdate().edit {
                showGameState(game, user.id.value, if (game.is_user1_turn) TTTState.O else TTTState.X)
                this.content = content
            }

            if (!won) {
                val loserTag = waitingUser?.userId?.let { "<@$it>" } ?: "Melijn"
                updateGameMessage("${user.mention} tied against $loserTag.")
                val balanceManager by KoinUtil.inject<BalanceManager>()
                balanceManager.add(user.id, game.bet)
                waitingUser?.userId?.let { balanceManager.add(Snowflake(it), game.bet) }
            } else {
                if (game.is_user1_turn && waitingUser == null) { // bot just played and won
                    updateGameMessage("Melijn won the game. ${user.mention} lost")
                } else { // user won, other user or bot lost
                    val melReward = if (game.bet > 0) " and gets ${game.bet * 2} mel" else ""
                    val loserTag = waitingUser?.userId?.let { "<@$it>" } ?: "Melijn"
                    updateGameMessage("${user.mention} won the game$melReward. $loserTag lost")
                    val balanceManager by KoinUtil.inject<BalanceManager>()
                    balanceManager.add(user.id, game.bet * 2)
                }
            }
            gameManager.delete(game)
        }

        private fun InteractionResponseModifyBuilder.showGameState(
            game: TicTacToeData,
            nextTurnUserId: ULong,
            nextMove: TTTState
        ) {
            embeds = mutableListOf()
            components = mutableListOf()
            val board = parseBoard(game.board)
            content = "It's <@${nextTurnUserId}>'s turn. You can play a `${nextMove.representation}`"
            for ((y, chunk) in board.chunked(3).withIndex()) {
                actionRow {
                    for ((x, state) in chunk.withIndex()) {
                        val customId = TICTACTOE_ACTION_PREFIX_ID + ((y * 3) + x)
                        interactionButton(ButtonStyle.Secondary, customId) {
                            label = state.representation
                            disabled = state != TTTState.EMPTY
                        }
                    }
                }
            }
        }

        fun serializeBoard(board: Iterable<TTTState>): String =
            board.joinToString(",") { it.ordinal.toString() }

        fun parseBoard(board: String) =
            board.split(",").map { TTTState.values()[it.toInt()] }
    }

    override suspend fun setup() {
        publicGuildSlashCommand(::TicTacToeArgs) {
            name = "tic-tac-toe"
            description = "Try to get a row of 3 x's or o's to win!"
            check {
                val gameByUser = gameManager.getGameByUser(event.interaction.user.id)
                failIf(gameByUser != null, tr("ttt.failYourAreInGame"))
            }
            action {
                val bet = arguments.bet
                val opponent = arguments.opponent

                val msgId = respond {
                    embedWithColor {
                        if (opponent == null) {
                            addGameMessage(user.asUser().tag)
                        } else {
                            addGameInviteMessage(user.asUser().tag, opponent.tag)
                        }
                    }

                }.id

                gameManager.setupGame(guild!!.id, channel.id, msgId, user.id, opponent?.id, bet ?: 0)
            }
        }
    }

    context(EmbedBuilder, MessageCreateBuilder)
    private fun addGameInviteMessage(
        inviter: String,
        invitee: String,
    ) {
        title = "tic-tac-toe invite"
        description = "$inviter invites $invitee"
        this@MessageCreateBuilder.actionRow {
            interactionButton(ButtonStyle.Success, TICTACTOE_ACCEPT_BUTTON_ID) {
                label = "Accept"
            }
            interactionButton(ButtonStyle.Danger, TICTACTOE_DENY_BUTTON_ID) {
                label = "Deny"
            }
        }
    }

    context(EmbedBuilder)
    private fun addGameMessage(
        userTag: String,
    ) {
        title = "tic-tac-toe"
        footer {
            text = "It's ${userTag}'s turn | Plays as " + TTTState.O.representation
        }
    }

    class TicTacToeArgs : Arguments() {
        private val balanceManager by KoinUtil.inject<BalanceManager>()
        private val gameManager by KoinUtil.inject<TicTacToeManager>()
        val opponent by optionalUser {
            name = "opponent"
            description = "Can be omitted if you wish to fight the bot instead."
            validate {
                val opponentId = this.value?.id ?: return@validate
                this.failIf(opponentId == this.context.getUser()?.id, tr("ttt.failOpponentIsSelf"))
                this.failIf(gameManager.getGameByUser(opponentId) != null, tr("ttt.failOpponentIsInGame"))
            }
        }
        val bet by optionalAvailableCurrency(
            "triedBettingNothing", "triedOverBetting"
        ) {
            name = "bet"
            description = "amount to bet"
            validate {
                failIf(tr("ttt.failOpponentInsufficientFunds")) { // check if opponent has enough mel
                    val bet = this.value ?: return@failIf false
                    (opponent?.let { balanceManager.get(it.id).balance } ?: 0) < bet
                }
            }
        }
    }

    enum class TTTState(val representation: String) {
        EMPTY(" "),
        X("X"),
        O("○"),
    }
}

private fun <T> Iterable<T>.allSame(exclude: T): Boolean {
    val first = this.firstOrNull() ?: return true
    if (first == exclude) return false
    for (s in this) {
        if (first != s) return false
    }
    return true
}

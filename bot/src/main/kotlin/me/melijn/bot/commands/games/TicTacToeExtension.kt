package me.melijn.bot.commands.games

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import me.melijn.bot.utils.KordExUtils.optionalAvailableCurrency

class TicTacToeExtension : Extension() {

    override val name: String = "tic-tac-toe"

    override suspend fun setup() {
        publicSlashCommand(::TicTacToeArgs) {

        }
    }

    class TicTacToeArgs : Arguments() {
        val opponent = optionalUser {
            name = "opponent"
            description = "Can be omitted if you wish to fight the bot instead."
        }
        val bet = optionalAvailableCurrency(
            "triedBettingNothing", "triedOverBetting"
        ) {
            name = "bet"
            description = "amount to bet"
        }
    }
}
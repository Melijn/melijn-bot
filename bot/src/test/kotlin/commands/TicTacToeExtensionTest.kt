package commands

import me.melijn.bot.commands.games.TicTacToeExtension
import me.melijn.bot.commands.games.TicTacToeExtension.TTTState
import me.melijn.bot.utils.Log
import org.junit.jupiter.api.Test

/** TicTacToe unit tests **/
class TicTacToeExtensionTest {

    private val log by Log

    @Test
    fun testIsGameWon() {
        val board1 = buildList { repeat(9) { add(TTTState.X) } }
        val board2 = buildList { repeat(9) { add(TTTState.O) } }
        val board3 = buildList { repeat(9) { add(TTTState.EMPTY) } }
        val board4 = listOf(
            TTTState.O, TTTState.O, TTTState.X,
            TTTState.EMPTY, TTTState.X, TTTState.EMPTY,
            TTTState.EMPTY, TTTState.O, TTTState.X
        )
        val boardDiag1 = listOf(
            TTTState.O, TTTState.EMPTY, TTTState.EMPTY,
            TTTState.EMPTY, TTTState.O, TTTState.EMPTY,
            TTTState.EMPTY, TTTState.EMPTY, TTTState.O
        )
        val boardDiag2 = listOf(
            TTTState.EMPTY, TTTState.EMPTY, TTTState.O,
            TTTState.EMPTY, TTTState.O, TTTState.EMPTY,
            TTTState.O, TTTState.EMPTY, TTTState.EMPTY,
        )
        assert(TicTacToeExtension.isGameWon(board1)) { log.error("Board of all X's should be done.") }
        assert(TicTacToeExtension.isGameWon(board2)) { log.error("Board of all O's should be done.") }
        assert(!TicTacToeExtension.isGameWon(board3)) { log.error("Board of all Empty's should not be done.") }
        assert(!TicTacToeExtension.isGameWon(board4)) { log.error("Board without 3X's or 3O's should not be done.") }
        assert(TicTacToeExtension.isGameWon(boardDiag1)) { log.error("Board with diagonal be done.") }
        assert(TicTacToeExtension.isGameWon(boardDiag2)) { log.error("Board with diagonal be done.") }
    }
}
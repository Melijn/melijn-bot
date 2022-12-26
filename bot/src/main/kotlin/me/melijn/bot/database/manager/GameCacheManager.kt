package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.games.TicTacToeExtension
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.TicTacToeData
import me.melijn.gen.TicTacToePlayerData
import me.melijn.gen.database.manager.AbstractTicTacToeManager
import me.melijn.gen.database.manager.AbstractTicTacToePlayerManager
import me.melijn.kordkommons.database.DriverManager
import java.util.*

@Inject
class TicTacToeGameManager(driverManager: DriverManager) : AbstractTicTacToeManager(driverManager) {
    fun deleteByGameId(gameId: UUID) {
        deleteById(gameId)
        driverManager.removeCacheEntry(gameId.toString())
    }
}

@Inject
class TicTacToePlayerManager(driverManager: DriverManager) : AbstractTicTacToePlayerManager(driverManager) {

}

@Inject
class TicTacToeManager(val driverManager: DriverManager) {

    val ticTacToeGameManager by KoinUtil.inject<TicTacToeGameManager>()
    val ticTacToePlayerManager by KoinUtil.inject<TicTacToePlayerManager>()

    /** Creates the database game entries **/
    fun setupGame(
        guildId: Snowflake,
        channelId: Snowflake,
        messageId: Snowflake,
        userId1: Snowflake,
        userId2: Snowflake?,
        bet: Long,
    ): TicTacToeData {
        val defaultBoard = buildList { repeat(9) { add(TicTacToeExtension.TTTState.EMPTY) } }
        val data = TicTacToeData(
            UUID.randomUUID(), guildId.value, channelId.value, messageId.value, true,
            TicTacToeExtension.serializeBoard(defaultBoard), bet
        )
        ticTacToeGameManager.store(data)
        ticTacToePlayerManager.store(TicTacToePlayerData(data.gameId, userId1.value, true))
        if (userId2 != null)
            ticTacToePlayerManager.store(TicTacToePlayerData(data.gameId, userId2.value, false))
        return data
    }

    suspend fun getGameByUser(id: Snowflake): TicTacToeData? {
        val player = ticTacToePlayerManager.getCachedById(id.value) ?: return null
        return ticTacToeGameManager.getCachedById(player.gameId)
    }

    fun delete(game: TicTacToeData) {
        val users = ticTacToePlayerManager.getByIndex1(game.gameId)
        users.forEach { ticTacToePlayerManager.delete(it) }
        ticTacToeGameManager.deleteByGameId(game.gameId)
    }

    fun updateGame(game: TicTacToeData) {
        ticTacToeGameManager.store(game)
    }
}
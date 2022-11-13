package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.TicTacToeData
import me.melijn.gen.TicTacToePlayerData
import me.melijn.gen.database.manager.AbstractTicTacToeManager
import me.melijn.gen.database.manager.AbstractTicTacToePlayerManager
import me.melijn.kordkommons.database.DriverManager
import java.util.*

@Inject
class TicTacToeGameManager(driverManager: DriverManager) : AbstractTicTacToeManager(driverManager) {

}

@Inject
class TicTacToePlayerManager(driverManager: DriverManager) : AbstractTicTacToePlayerManager(driverManager) {

}

@Inject
class TicTacToeManager(driverManager: DriverManager) {
    val ticTacToeGameManager by KoinUtil.inject<TicTacToeGameManager>()
    val ticTacToePlayerManager by KoinUtil.inject<TicTacToePlayerManager>()

    fun createGame(
        guildId: Snowflake,
        channelId: Snowflake,
        messageId: Snowflake,
        userId1: Snowflake,
        userId2: Snowflake?,
        bet: Long,
    ): TicTacToeData {
        val data = TicTacToeData(
            UUID.randomUUID(), guildId.value, channelId.value, messageId.value, true,
            "[,,,,,,,,]", bet
        )
        ticTacToeGameManager.store(data)
        ticTacToePlayerManager.store(TicTacToePlayerData(data.gameId, userId1.value, true))
        if (userId2 != null)
            ticTacToePlayerManager.store(TicTacToePlayerData(data.gameId, userId2.value, false))
        return data
    }
}
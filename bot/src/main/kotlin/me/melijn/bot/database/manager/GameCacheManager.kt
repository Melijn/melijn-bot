package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.games.TicTacToeExtension
import me.melijn.bot.database.model.TicTacToe
import me.melijn.bot.database.model.TicTacToePlayer
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.TicTacToeData
import me.melijn.gen.TicTacToePlayerData
import me.melijn.gen.database.manager.AbstractTicTacToeManager
import me.melijn.gen.database.manager.AbstractTicTacToePlayerManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import java.util.*

@Inject
class TicTacToeGameManager(driverManager: DriverManager) : AbstractTicTacToeManager(driverManager) {}

@Inject
class TicTacToePlayerManager(driverManager: DriverManager) : AbstractTicTacToePlayerManager(driverManager) {}

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
            Clock.System.now(), TicTacToeExtension.serializeBoard(defaultBoard), bet
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
        ticTacToeGameManager.deleteById(game.gameId)
    }

    fun updateGame(game: TicTacToeData) {
        ticTacToeGameManager.store(game)
    }

    fun getOlderGames(lastMoveCutoffMoment: Instant): List<Triple<TicTacToeData, Long, Long?>> {
        val mutableList = mutableListOf<Triple<TicTacToeData, Long, Long?>>()
        ticTacToeGameManager.scopedTransaction {
            val res = TicTacToe.join(TicTacToePlayer, JoinType.INNER).select {
                TicTacToe.last_played.less(lastMoveCutoffMoment)
            }.sortedBy { TicTacToe.gameId }.distinct()

            var entry1: Pair<TicTacToeData, Long>? = null
            var entry2: Long? = null
            for (row in res) {
                val data = TicTacToeData.fromResRow(row)
                val fromResRow = TicTacToePlayerData.fromResRow(row)
                if (entry1?.first?.gameId != data.gameId) {
                    // store entries
                    if (entry1 != null) mutableList.add(Triple(entry1.first, entry1.second, entry2))
                    // reset entries
                    entry1 = null
                    entry2 = null
                    // store current dbrow in entry1
                    entry1 = data to fromResRow.userId.toLong()
                } else {
                    entry2 = fromResRow.userId.toLong()
                    // store current dbrow in entry2
                }
                println("$data - $fromResRow")
            }
        }
        return mutableList
    }

    fun deleteOlderGames(lastMoveCutoffMoment: Instant) {
        ticTacToeGameManager.scopedTransaction {
            TicTacToe.deleteWhere { last_played.less(lastMoveCutoffMoment) }
        }
    }
}
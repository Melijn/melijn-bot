package me.melijn.bot.database.manager

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
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.UserSnowflake
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
    suspend fun setupGame(
        guildId: ISnowflake,
        channelId: ISnowflake,
        messageId: ISnowflake,
        userId1: UserSnowflake,
        userId2: UserSnowflake?,
        bet: Long,
    ): TicTacToeData {
        val defaultBoard = buildList { repeat(9) { add(TicTacToeExtension.TTTState.EMPTY) } }
        val data = TicTacToeData(
            UUID.randomUUID(), guildId.idLong, channelId.idLong, messageId.idLong, true,
            Clock.System.now(), TicTacToeExtension.serializeBoard(defaultBoard), bet
        )
        ticTacToeGameManager.store(data)
        ticTacToePlayerManager.store(TicTacToePlayerData(data.gameId, userId1.idLong, true))
        if (userId2 != null)
            ticTacToePlayerManager.store(TicTacToePlayerData(data.gameId, userId2.idLong, false))
        return data
    }

    suspend fun getGameByUser(id: UserSnowflake): TicTacToeData? {
        val player = ticTacToePlayerManager.getById(id.idLong) ?: return null
        return ticTacToeGameManager.getById(player.gameId)
    }

    suspend fun delete(game: TicTacToeData) {
        val users = ticTacToePlayerManager.getByIndex1(game.gameId)
        users.forEach { ticTacToePlayerManager.deleteById(it.userId) }
        ticTacToeGameManager.deleteById(game.gameId)
    }

    suspend fun updateGame(game: TicTacToeData) {
        ticTacToeGameManager.store(game)
    }

    suspend fun getOlderGames(lastMoveCutoffMoment: Instant): List<Triple<TicTacToeData, Long, Long?>> {
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

    suspend fun deleteOlderGames(lastMoveCutoffMoment: Instant) {
        ticTacToeGameManager.scopedTransaction {
            TicTacToe.deleteWhere { last_played.less(lastMoveCutoffMoment) }
        }
    }
}
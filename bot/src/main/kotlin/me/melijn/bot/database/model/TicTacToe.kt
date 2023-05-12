package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
@TableModel(true, 90, false)
object TicTacToe : Table("tictactoe") {
    val gameId = uuid("game_id")

    val guildId = long("guild_id")
    val channelId = long("channel_id")
    val messageId = long("message_id")

    // true = user1, false = user2
    val is_user1_turn = bool("is_user1_turn")

    val last_played = timestamp("last_played")

    val board = text("board_state")
    val bet = long("bet").default(0)

    override val primaryKey: PrimaryKey = PrimaryKey(gameId)

    init {
        index(true, guildId, channelId, messageId)
    }
}

// Separate table for caching purposes
@CreateTable
@TableModel(true, 90, false)
object TicTacToePlayer : Table("tictactoe_player") {
    val gameId = uuid("game_id").references(TicTacToe.gameId, ReferenceOption.CASCADE)
    val userId = long("user_id")
    val isUser1 = bool("is_user_1")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

    init {
        index(true, gameId, isUser1)
        index(false, gameId)
    }
}
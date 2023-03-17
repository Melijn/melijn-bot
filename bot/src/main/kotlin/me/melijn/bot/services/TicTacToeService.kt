package me.melijn.bot.services

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.channel.GuildMessageChannel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.BalanceManager
import me.melijn.bot.database.manager.TicTacToeManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.kordkommons.async.RunnableTask
import me.melijn.kordkommons.async.TaskManager
import kotlin.time.Duration.Companion.seconds

val maxMoveDuration = 90.seconds

@Inject(true)
class TicTacToeService : Service("tic-tac-toe", maxMoveDuration.times(0), maxMoveDuration) {

    val tttManager by inject<TicTacToeManager>()

    override val service: RunnableTask = RunnableTask {
        val lastMoveCutoffMoment = Clock.System.now().minus(maxMoveDuration)
        val games = tttManager.getOlderGames(lastMoveCutoffMoment)
        tttManager.deleteOlderGames(lastMoveCutoffMoment)

        TaskManager.async {
            for (game in games) { // go over expired games

                val kord by inject<Kord>()
                val gameData = game.first
                val message = kord.getGuildOrNull(Snowflake(gameData.guildId))
                    ?.getChannelOfOrNull<GuildMessageChannel>(Snowflake(gameData.channelId))
                    ?.getMessageOrNull(Snowflake(gameData.messageId))

                delay(2000)

                if (message == null) continue // keep this after the delay so we don't spam fetch missing messages
                // update the message
                message.edit {
                    content = if(gameData.is_user1_turn) "user1"
                    else "user2"
                }

                // give the active player all the mel
                val balanceManager by inject<BalanceManager>()
                val target = if (gameData.is_user1_turn) game.second else game.third ?: game.second
                balanceManager.add(Snowflake(target), gameData.bet * 2)
            }
        }
    }
}
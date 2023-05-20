package me.melijn.bot.services


import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.MessageEdit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.BalanceManager
import me.melijn.bot.database.manager.TicTacToeManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.kordkommons.async.RunnableTask
import me.melijn.kordkommons.async.TaskScope
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration.Companion.seconds

val maxMoveDuration = 90.seconds

@Inject(true)
class TicTacToeService : Service("tic-tac-toe", preFetchDuration.times(0), preFetchDuration) {

    val tttManager by inject<TicTacToeManager>()

    override val service: RunnableTask = RunnableTask {
        val lastMoveCutoffMoment = Clock.System.now().minus(preFetchDuration)
        val games = tttManager.getOlderGames(lastMoveCutoffMoment)
        tttManager.deleteOlderGames(lastMoveCutoffMoment)

        TaskScope.launch {
            for (game in games) { // go over expired games

                val kord by inject<ShardManager>()
                val gameData = game.first
                val message = kord.getGuildById(gameData.guildId)
                    ?.getChannelById(GuildMessageChannel::class.java, gameData.channelId)
                    ?.retrieveMessageById(gameData.messageId)?.await()

                delay(2000)

                if (message == null) continue // keep this after the delay so we don't spam fetch missing messages
                // update the message
                message.editMessage(MessageEdit {
                    content = if (gameData.is_user1_turn) "user1"
                    else "user2"
                }).await()

                // give the active player all the mel
                val balanceManager by inject<BalanceManager>()
                val target = if (gameData.is_user1_turn) game.second else game.third ?: game.second
                balanceManager.add(UserSnowflake.fromId(target), gameData.bet * 2)
            }
        }
    }
}
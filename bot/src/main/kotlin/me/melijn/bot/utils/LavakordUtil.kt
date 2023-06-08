package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.utils.getKoin
import com.kotlindiscord.kord.extensions.utils.scheduling.TaskConfig
import dev.schlaubi.lavakord.audio.retry.Retry
import dev.schlaubi.lavakord.jda.LavaKordShardManager
import dev.schlaubi.lavakord.jda.lavakord
import kotlinx.coroutines.delay
import me.melijn.bot.Melijn
import me.melijn.bot.music.MusicManager.registerReconnectHandler
import me.melijn.gen.Settings
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun loadLavaLink(lShardManager: LavaKordShardManager) {
    val kord by getKoin().inject<ShardManager>()
    Melijn.lavalink = kord.lavakord(lShardManager, TaskConfig.dispatcher) {
        link {
            autoReconnect = true
            retry = RealLinearRetry(1.seconds, 60.seconds, Int.MAX_VALUE)
        }
    }

    val nodeUrls = Settings.lavalink.url
    val nodePasswords = Settings.lavalink.password

    for (i in nodeUrls.indices) {
        Melijn.lavalink.addNode(nodeUrls[i], nodePasswords[i], "node$i")
    }
    for (node in Melijn.lavalink.nodes) {
        node.registerReconnectHandler()
    }
}

internal class RealLinearRetry(
    private val firstBackoff: Duration,
    private val maxBackoff: Duration,
    private val maxTries: Int
) : Retry {

    val log by Log

    init {
        require(firstBackoff.isPositive()) { "firstBackoff needs to be positive but was ${firstBackoff.inWholeMilliseconds} ms" }
        require(maxBackoff.isPositive()) { "maxBackoff needs to be positive but was ${maxBackoff.inWholeMilliseconds} ms" }
        require(
            maxBackoff.minus(firstBackoff).isPositive()
        ) { "maxBackoff ${maxBackoff.inWholeMilliseconds} ms needs to be bigger than firstBackoff ${firstBackoff.inWholeMilliseconds} ms" }
        require(maxTries > 0) { "maxTries needs to be positive but was $maxTries" }
    }

    private val tries = AtomicInteger(0)

    override val hasNext: Boolean
        get() = tries.get() < maxTries

    override fun reset() {
        tries.set(0)
    }

    override suspend fun retry() {
        if (!hasNext) error("max retries exceeded")
        val diff =
            firstBackoff.inWholeMilliseconds +
                    ((tries.incrementAndGet() / maxTries) * (maxBackoff.inWholeMilliseconds - firstBackoff.inWholeMilliseconds))
        log.info { "retry attempt ${tries.get()}/$maxTries, delaying for $diff ms." }
        delay(diff)
    }
}
package me.melijn.bot.utils

import dev.schlaubi.lavakord.audio.retry.Retry
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

internal class RealLinearRetry constructor(
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
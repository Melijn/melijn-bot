package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.UsageHistory
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.CooldownHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitType
import kotlinx.datetime.Instant

class MelUsageHistory(
    override val cooldownHits: List<Instant>,
    override val rateLimitHits: List<Instant>,
    override val rateLimitState: Boolean,
    override val usages: List<Instant>,
) : UsageHistory, RateLimitHistory, CooldownHistory {

    /**
     * Instead of changing the [UsageHistory] state we track our usageHistory via [changes]
     * this way we can keep using the [RateLimitType] api which only offers a getter and setter for [UsageHistory].
     * The database can then be updated with only the new and removed data.
     *
     * Inner state is mutable e.g.
     * ```
     * changes.usageChanges.added.add(number)
     * ``` **/
    val changes = ChangeWrapper()

    data class ChangeWrapper(
        val usageChanges: ChangeList = ChangeList(),
        val crossedLimitChanges: ChangeList = ChangeList(),
        val crossedCooldownsChanges: ChangeList = ChangeList(),
    ) {
        data class ChangeList(
            val added: MutableList<Instant> = ArrayList(),
            var removeUnder: Instant? = null
        )
    }

    override suspend fun addUsage(moment: Instant) {
        changes.usageChanges.added.add(moment)
    }

    override suspend fun removeExpiredUsages(cutoffTime: Instant) {
        changes.usageChanges.removeUnder = changes.usageChanges.removeUnder?.let { maxOf(it, cutoffTime) } ?: cutoffTime
    }

    /** Adds a rateLimitHit moment to the usageHistory. **/
    override suspend fun addRateLimitHit(moment: Instant) {
        changes.crossedLimitChanges.added.add(moment)
    }

    /** RateLimitHit moments before [cutoffTime] will be removed from the usageHistory. **/
    override suspend fun removeExpiredRateLimitHits(cutoffTime: Instant) {
        changes.crossedLimitChanges.removeUnder =
            changes.crossedLimitChanges.removeUnder?.let { maxOf(it, cutoffTime) } ?: cutoffTime
    }

    override fun addCooldownHit(moment: Instant) {
        changes.crossedCooldownsChanges.added.add(moment)
    }

    override fun removeExpiredCooldownHits(cutoffTime: Instant) {
        changes.crossedCooldownsChanges.removeUnder =
            changes.crossedCooldownsChanges.removeUnder?.let { maxOf(it, cutoffTime) } ?: cutoffTime
    }
}
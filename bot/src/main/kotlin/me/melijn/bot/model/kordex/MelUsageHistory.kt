package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitType
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistory
import java.lang.Long.max

class MelUsageHistory(
    override val crossedCooldowns: List<Long>,
    override val crossedLimits: List<Long>,
    override val rateLimitState: Boolean,
    override val usages: List<Long>
) : UsageHistory {

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
            val added: MutableList<Long> = ArrayList(),
            var removeUnder: Long? = null
        )
    }

    override fun addCrossedCooldown(moment: Long) {
        changes.crossedCooldownsChanges.added.add(moment)
    }

    override fun addCrossedLimit(moment: Long) {
        changes.crossedLimitChanges.added.add(moment)
    }

    override fun addUsage(moment: Long) {
        changes.usageChanges.added.add(moment)
    }

    override fun removeExpiredCrossedCooldowns(cutoffTime: Long) {
        changes.crossedCooldownsChanges.removeUnder =
            changes.crossedCooldownsChanges.removeUnder?.let { max(it, cutoffTime) } ?: cutoffTime
    }

    override fun removeExpiredCrossedLimits(cutoffTime: Long) {
        changes.crossedLimitChanges.removeUnder =
            changes.crossedLimitChanges.removeUnder?.let { max(it, cutoffTime) } ?: cutoffTime
    }

    override fun removeExpiredUsages(cutoffTime: Long) {
        changes.usageChanges.removeUnder = changes.usageChanges.removeUnder?.let { max(it, cutoffTime) } ?: cutoffTime
    }
}
package me.melijn.bot.model.kordex

import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitType
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.UsageHistory

interface MelRateLimitType : RateLimitType {
    override fun getUsageHistory(context: DiscriminatingContext): MelUsageHistory

    fun setUsageHistory(context: DiscriminatingContext, usageHistory: MelUsageHistory)
    override fun setUsageHistory(context: DiscriminatingContext, usageHistory: UsageHistory) {
        setUsageHistory(context, usageHistory as MelUsageHistory)
    }
}
package me.melijn.bot.model

import me.melijn.bot.model.enums.IntLimit
import me.melijn.bot.model.enums.PremiumTier


data class PremiumIntLimit(

    /**
     * null -> no limit
     **/
    val freeAmount: Int?,

    /**
     * higher premium tiers can inherit from the lower tiers amount when the [IntLimit.type] is [IntLimit.Type.INHERIT]
     * The other types should be self explaining
     **/
    val premiumAmountLimit: (PremiumTier) -> IntLimit
)
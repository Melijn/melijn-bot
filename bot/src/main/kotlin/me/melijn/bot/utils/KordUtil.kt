package me.melijn.bot.utils

import dev.kord.core.entity.User

object KordUtil {
    fun User.effectiveAvatarUrl(): String {
        return User.Avatar(data, kord).url
    }
}
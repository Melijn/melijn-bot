package me.melijn.bot.model

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User

@kotlinx.serialization.Serializable
data class PartialUser(
    val id: Snowflake,
    val username: String,
    val discriminator: String,
    val avatarUrl: String?
) {
    val idULong = id.value
    val tag
        get() = "${username}#${discriminator}"
    val effectiveAvatarUrl
        get() = avatarUrl ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

    companion object {
        fun fromKordUser(user: User) = user.run {
            PartialUser(id, username, discriminator, avatar?.url)
        }
    }

}
package me.melijn.bot.model

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake

@kotlinx.serialization.Serializable
data class PartialUser(
    val userFlake: UserSnowflake,
    val username: String,
    val discriminator: String,
    val avatarUrl: String?
) {
    val idULong = userFlake.idLong.toULong()
    val tag
        get() = "${username}#${discriminator}"
    val effectiveAvatarUrl
        get() = avatarUrl ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

    companion object {
        fun fromKordUser(user: User) = user.run {
            PartialUser(this, name, discriminator, effectiveAvatarUrl)
        }
    }

}
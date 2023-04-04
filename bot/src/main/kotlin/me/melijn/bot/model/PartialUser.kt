package me.melijn.bot.model

import net.dv8tion.jda.api.entities.User

@kotlinx.serialization.Serializable
data class PartialUser(
    val idLong: Long,
    val username: String,
    val discriminator: String,
    val avatarUrl: String?
) {
    val idULong = idLong.toULong()
    val tag
        get() = "${username}#${discriminator}"
    val effectiveAvatarUrl
        get() = avatarUrl ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

    companion object {
        fun fromKordUser(user: User) = user.run {
            PartialUser(this.idLong, name, discriminator, effectiveAvatarUrl)
        }
    }

}
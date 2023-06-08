package me.melijn.bot.model

import net.dv8tion.jda.api.entities.User

@kotlinx.serialization.Serializable
data class PartialUser(
    val idLong: Long,
    val username: String,
    val discriminator: String,
    val avatarUrl: String?
) {
    val tag
        get() = "${username}#${discriminator}"

    companion object {
        fun fromKordUser(user: User) = user.run {
            PartialUser(this.idLong, name, discriminator, effectiveAvatarUrl)
        }
    }

}
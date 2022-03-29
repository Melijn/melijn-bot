package model

@kotlinx.serialization.Serializable
data class PartialDiscordUser(
    val id: Long,
    val username: String,
    val discriminator: String,
    val avatar: String?
)
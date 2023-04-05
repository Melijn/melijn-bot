package me.melijn.bot.model

import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction

data class OwnedGuildMessage(
    val guildId: Long,
    override val channelId: Long,
    override val ownerId: Long,
    override val messageId: Long
) : AbstractOwnedMessage(ownerId, channelId, messageId) {

    companion object {
        fun from(interaction: ButtonInteraction): OwnedGuildMessage {
            val message = interaction.message
            return from(interaction.guild!!, interaction.user, message)
        }

        fun from(guild: ISnowflake, user: UserSnowflake, message: Message): OwnedGuildMessage {
            return OwnedGuildMessage(
                guild.idLong,
                message.channel.idLong,
                user.idLong,
                message.idLong
            )
        }
    }
}


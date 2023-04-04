package me.melijn.bot.model

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction

open class OwnedPrivateMessage(
    override val ownerId: Long,
    override val channelId: Long,
    override val messageId: Long
) : AbstractOwnedMessage(ownerId, channelId, messageId) {

    companion object {
        fun from(interaction: ButtonInteraction): OwnedPrivateMessage = from(interaction.user,  interaction.message)
        fun from(user: UserSnowflake, message: Message): OwnedPrivateMessage {
            return OwnedPrivateMessage(
                message.channel.idLong,
                user.idLong,
                message.idLong
            )
        }
    }
}
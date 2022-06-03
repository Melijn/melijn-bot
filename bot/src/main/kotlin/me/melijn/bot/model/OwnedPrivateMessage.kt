package me.melijn.bot.model

import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.entity.interaction.ButtonInteraction

open class OwnedPrivateMessage(
    override val ownerId: Long,
    override val channelId: Long,
    override val messageId: Long
) : AbstractOwnedMessage(ownerId, channelId, messageId) {

    companion object {
        fun from(interaction: ButtonInteraction): OwnedPrivateMessage = from(interaction.user,  interaction.message)
        fun from(user: UserBehavior, message: MessageBehavior): OwnedPrivateMessage {
            return OwnedPrivateMessage(
                user.id.value.toLong(),
                message.channel.id.value.toLong(),
                message.id.value.toLong()
            )
        }
    }
}
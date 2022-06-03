package me.melijn.bot.model

import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.GuildButtonInteraction

/**
 * Represents a discord message 'owned' by another user (for example the invoker of a command owns the melijn response, used for intractable menus)
 */
abstract class AbstractOwnedMessage(
    open val ownerId: Long,
    open val channelId: Long,
    open val messageId: Long
) {

    companion object {
        fun from(interaction: ButtonInteraction): AbstractOwnedMessage = when (interaction) {
            is GuildButtonInteraction -> OwnedGuildMessage.from(interaction)
            else -> OwnedPrivateMessage.from(interaction)
        }

        fun from(guild: GuildBehavior?, user: UserBehavior, message: MessageBehavior): AbstractOwnedMessage =
            if (guild == null) OwnedPrivateMessage(
                message.channel.id.value.toLong(),
                user.id.value.toLong(),
                message.id.value.toLong()
            ) else OwnedGuildMessage(
                guild.id.value.toLong(),
                message.channel.id.value.toLong(),
                user.id.value.toLong(),
                message.id.value.toLong()
            )
    }

    // Intellij generated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractOwnedMessage

        if (ownerId != other.ownerId) return false
        if (channelId != other.channelId) return false
        if (messageId != other.messageId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ownerId.hashCode()
        result = 31 * result + channelId.hashCode()
        result = 31 * result + messageId.hashCode()
        return result
    }
}
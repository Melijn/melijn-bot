package me.melijn.bot.model

import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction


/**
 * Represents a discord message 'owned' by another user (for example the invoker of a command owns the melijn response, used for intractable menus)
 */
abstract class AbstractOwnedMessage(
    open val ownerId: Long,
    open val channelId: Long,
    open val messageId: Long
) {

    companion object {
        fun from(interaction: ButtonInteraction): AbstractOwnedMessage = OwnedPrivateMessage.from(interaction)

        fun from(guild: ISnowflake?, user: UserSnowflake, message: Message): AbstractOwnedMessage =
            if (guild == null) OwnedPrivateMessage(
                message.channel.idLong,
                user.idLong,
                message.idLong
            ) else OwnedGuildMessage(
                guild.idLong,
                message.channel.idLong,
                user.idLong,
                message.idLong
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
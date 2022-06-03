package me.melijn.bot.model

import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.entity.interaction.GuildButtonInteraction

data class OwnedGuildMessage(
    val guildId: Long,
    override val channelId: Long,
    override val ownerId: Long,
    override val messageId: Long
) : AbstractOwnedMessage(ownerId, channelId, messageId) {

    companion object {
        fun from(interaction: GuildButtonInteraction): OwnedGuildMessage {
            val message = interaction.message
            return from(interaction.guild, interaction.user, message)
        }

        fun from(guild: GuildBehavior, user: UserBehavior, message: MessageBehavior): OwnedGuildMessage {
            return OwnedGuildMessage(
                guild.id.value.toLong(),
                message.channel.id.value.toLong(),
                user.id.value.toLong(),
                message.id.value.toLong()
            )
        }
    }
}


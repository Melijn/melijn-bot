package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.MessageChannel
import me.melijn.apkordex.command.KordExtension

@KordExtension
class DevExtension : Extension() {

    override val name: String = "dev"

    override suspend fun setup() {
        publicSlashCommand {
            name = "test"
            description = "test"
            action {
                respond {
                    content = ":flushed:"
                }
            }
        }
        chatCommand(::DumpMessageArgs) {
            name = "dump"
            description = "dump message content and embed"
            action {
                val s = arguments.messageLink.parsed
                val parts = s.split("/").takeLast(3).map { it.toULong() }
                val guild = this@DevExtension.kord.getGuild(Snowflake(parts[0]))
                val channel = guild?.getChannelOrNull(Snowflake(parts[1]))
                if (channel == null || channel !is MessageChannel) {
                    this.channel.createMessage("that link is veeery stinky")
                    return@action
                }
                val message = channel.getMessage(Snowflake(parts[2]))
                this.channel.createMessage {
                    content = "```\n" +
                        "content: " + message.content.replace("```", "`\\``") + "\n" +
                        "embed: [\n" + message.embeds.joinToString(",\n") { it.toString() } +
                        "\n]\n" +
                        "attachments: [\n" + message.attachments.joinToString("\n") { it.filename + " - " + it.url } +
                        "\n]\n" +

                        "```"
                }
            }
        }
    }

    class DumpMessageArgs : Arguments() {
        val messageLink = string {
            name = "messageLink"
            description = "discord formatted message link"
        }
    }
}

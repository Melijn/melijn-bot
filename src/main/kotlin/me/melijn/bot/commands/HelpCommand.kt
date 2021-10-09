package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.rest.builder.message.create.embed

class HelpCommand : Extension() {
    override val name: String = "yardim"

    override suspend fun setup() {
        chatCommand {
            name = "help"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
            }

            action {
                this.event.message.channel.createEmbed {
                    this.title = "help"
                    this.description = "here is help info"
                }
            }
        }

        ephemeralSlashCommand {
            name = "help"
            description = "back at it again"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
            }

            action {
                this.respond {
                    this.embed {
                        this.title = "help"
                        this.description = "here is help info"
                    }
                }
            }
        }
    }
}
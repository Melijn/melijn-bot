package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.rest.builder.message.create.embed
import me.melijn.bot.database.manager.GuildSettingsManager
import org.koin.core.component.inject

class SettingsCommand : Extension() {

    override val name: String = "settings"
    val guildSettingsManager: GuildSettingsManager by inject()

    override suspend fun setup() {
        chatCommand {
            name = "settings"
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
            val cmd = this
            name = "settings"
            description = "fish"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
            }

            action {
                this.respond {
                    this.embed {
                        this.title = "settings"
                        this.description = cmd.guildId?.let { guildSettingsManager.get(it) }.toString()
                    }
                }
            }
        }
    }
}
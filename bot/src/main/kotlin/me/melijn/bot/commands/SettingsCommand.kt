package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.rest.builder.message.create.embed
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.gen.GuildSettingsData
import org.koin.core.component.inject

class SettingsCommand : Extension() {

    override val name: String = "settings"
    val guildSettingsManager: GuildSettingsManager by inject()

    override suspend fun setup() {
        ephemeralSlashCommand {
            val cmd = this
            name = "settings"
            description = "fish"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
            }

            ephemeralSubCommand() {
                name = "view"
                description = "viewing"
                action {
                    this.respond {
                        this.embed {
                            this.title = "settings"
                            this.description = cmd.guildId?.let { guildSettingsManager.get(it)?.allowNsfw }.toString()
                        }
                    }
                }
            }
            description = "saving group"
            ephemeralSubCommand(::SetArg) {
                name = "set"
                description = "sets setting"
                action {
                    val guild = this.guild!!
                    val settings = guildSettingsManager.get(guild.id) ?: GuildSettingsData(
                        guild.id.value, ">", allowNsfw = false, allowNsfw2 = false
                    )
                    if (this.arguments.target == "true") {
                        settings.allowNsfw = true
                    } else if (this.arguments.target == "false") {
                        settings.allowNsfw = false
                    }
                    guildSettingsManager.store(settings)
                    this.respond {
                        this.content = "updated"
                    }
                }
            }

        }
    }

    inner class SetArg : Arguments() {
        val target by stringChoice {
            name = "user"
            description = "Person you'd like to view"
            this.choice("nsfw", "true")
            this.choice("no nsfw", "false")
        }
    }

}
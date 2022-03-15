package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import org.koin.core.component.inject

class HelpCommand : Extension() {

    override val name: String = "yardim"

    private val chatCommandsRegistry: ChatCommandRegistry by inject()
    private val applicationCommands: ApplicationCommandRegistry by inject()


    override suspend fun setup() {
        chatGroupCommand(::HelpArgs) {
            name = "help"
            description = "back at it again"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
            }

            action {
                if (this.arguments.command.parseSuccess) {
                    val cmd = this.arguments.command.parsed!!
                    this.channel.createMessage {
                        content = "help about $cmd :troll"
                    }
                    return@action
                }

                this.channel.createEmbed {
                    this.title = "useful help menu"
                    this.description = "waa waa"
                }
            }

            chatCommand {
                name = "list"
                description = "list all cmds"


                action {
                    val chatCommandsStr = chatCommandsRegistry.commands.joinToString(", ") {
                        "`${it.name}`"
                    }
                    val applicationCommands = applicationCommands as DefaultApplicationCommandRegistry
                    val slashCommandsStr = applicationCommands.slashCommands.entries.joinToString(", ") {
                        "`${it.value.name}`"
                    }
                    val userCommandsStr = applicationCommands.userCommands.entries.joinToString(", ") {
                        "`${it.value.name}`"
                    }
                    val messageCommandsStr = applicationCommands.messageCommands.entries.joinToString(", ") {
                        "`${it.value.name}`"
                    }

                    this.channel.createEmbed {
                        this.title = "help"
                        this.description = """
                            **Chat Commands**
                            $chatCommandsStr
                            **Slash Commands**
                            $slashCommandsStr
                            **User Commands**
                            $userCommandsStr
                            **Message Commands**
                            $messageCommandsStr
                        """.trimIndent()
                    }
                }
            }
        }
    }

    inner class HelpArgs : Arguments() {
        val command = optionalString {
            name = "commandName"
            description = "name of a command you want help about"
        }
    }
}
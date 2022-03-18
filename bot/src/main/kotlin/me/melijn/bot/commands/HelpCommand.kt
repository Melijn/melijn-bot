package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.SingleToOptionalConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createEmbed
import me.melijn.bot.database.manager.PrefixManager
import org.koin.core.component.inject

class HelpCommand : Extension() {

    override val name: String = "yardim"

    private val chatCommandsRegistry: ChatCommandRegistry by inject()
    private val applicationCommands: ApplicationCommandRegistry by inject()
    private val prefixManager: PrefixManager by inject()

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
                    val command = getCommandHelp(this.argString)
                    this.channel.createEmbed {
                        title = cmd
                        description = command
                    }
                    return@action
                }
                val bot = this@chatGroupCommand.kord.getSelf()

                val prefix = prefixManager.getPrefixes(guild!!.id).minByOrNull { it.prefix.length }?.prefix ?: ">"

                this.channel.createEmbed {
                    this.title = "Help Menu"
                    this.description = """
                        __**Prefix:**__ `${prefix}`
                        __**Commands:**__ `${prefix}help list` or on the **[website](https://melijn.com)**
                        __**Invite:**__ **[link](https://melijn.com/invite)**
                        
                        **Command Help:** `${prefix}help <command>` (ex. `${prefix}help play`)
                        """.trimIndent()
                    this.footer {
                        this.text = "@${bot.tag} can always be used as prefix"
                    }
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

    private fun getCommandHelp(argString: String): String? {
        val applicationCommands = applicationCommands as DefaultApplicationCommandRegistry
        val chatCommand = chatCommandsRegistry.commands.firstOrNull {
            argString.startsWith(it.name)
        }
        val slashCommand by lazy {
            applicationCommands.slashCommands.entries.map { it.value }.firstOrNull {
                argString.startsWith(it.name)
            }
        }
        val userCommand by lazy {
            applicationCommands.userCommands.entries.map { it.value }.firstOrNull {
                argString.startsWith(it.name)
            }
        }
        val messageCommand by lazy {
            applicationCommands.messageCommands.entries.map { it.value }.firstOrNull {
                argString.startsWith(it.name)
            }
        }
        val fullHelp = when {
            chatCommand != null -> {
                """
                    Syntax: ${chatCommand.name} ${
                    chatCommand.arguments?.let { it() }?.args?.joinToString(" ") {
                        if (it.converter is SingleToOptionalConverter<*>) "[${it.displayName}]"
                        else "<${it.displayName}>"
                    }
                }
                    ${if (chatCommand.aliases.isNotEmpty()) "Aliases: ${chatCommand.aliases.joinToString(" ") { "`$it`" }}" else ""}
                    Desc: ${chatCommand.description}
                    **Args**
                    ${chatCommand.arguments?.let { it() }?.args?.joinToString("\n") { "`${it.displayName}` ${it.description}" }}
                """.trimIndent()
            }
            slashCommand != null -> {
                val slashCommand = slashCommand!!
                """
                    Syntax: `${slashCommand.name} ${
                    slashCommand.arguments?.let { it() }?.args?.joinToString(" ") {
                        if (it.converter is SingleToOptionalConverter<*>) "[${it.displayName}]"
                        else "<${it.displayName}>"
                    }
                }`
                    Desc: ${slashCommand.description}
                    **Args**
                    ${slashCommand.arguments?.let { it() }?.args?.joinToString("\n") { "`${it.displayName}` ${it.description}" }}
                """.trimIndent()
            }
            userCommand != null -> {
                val userCommand = userCommand!!
                """
                    Syntax: ${userCommand.name}
                """.trimIndent()
            }
            messageCommand != null -> {
                val messageCommand = messageCommand!!
                """
                    Syntax: ${messageCommand.name}
                """.trimIndent()
            }
            else -> {
                "no command"
            }
        }

        return fullHelp
    }

    inner class HelpArgs : Arguments() {
        val command = optionalString {
            name = "commandName"
            description = "name of a command you want help about"
        }
    }

//    override suspend fun formatCommandHelp(
//        prefix: String,
//        event: MessageCreateEvent,
//        command: ChatCommand<out Arguments>,
//        longDescription: Boolean
//    ): Triple<String, String, String> {
//        return Triple(
//            command.name,
//            prefix + command.name,
//            command.arguments?.invoke()?.args?.joinToString(" ") { "<${it.displayName}>" } ?: "")
//    }
}
package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.Command
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.SingleToOptionalConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.interactions.components.primary
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.PrefixManager
import me.melijn.bot.utils.JDAUtil.createEmbed
import me.melijn.bot.utils.KordExUtils.respond
import net.dv8tion.jda.api.Permission
import org.koin.core.component.inject

@KordExtension
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
                requireBotPermissions(Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)
            }

            action {
                if (arguments.command.parseSuccess) {
                    val cmd = arguments.command.parsed!!
                    val command = getCommandHelp(argString)

                    respond {
                        embed {
                            title = cmd
                            description = command
                        }
                    }
                    return@action
                }
                val bot = this.event.jda.selfUser

                val prefix = prefixManager.getPrefixes(guild!!).minByOrNull { it.prefix.length }?.prefix ?: ">"

                respond {
                    embed {
                        title = "Help Menu"
                        description = """
                        __**Prefix:**__ `${prefix}`
                        __**Commands:**__ `${prefix}help list` or on the **[website](https://melijn.com)**
                        __**Invite:**__ **[link](https://melijn.com/invite)**
                        
                        **Command Help:** `${prefix}help <command>` (ex. `${prefix}help play`)
                        """.trimIndent()
                        footer {
                            name = "${bot.asMention} can always be used as prefix"
                        }

                    }
                    primary("commands", "Command List")
                    link("https://melijn.com/legal", "Privacy Policy")
                }
            }

            chatCommand {
                name = "list"
                description = "list all cmds"

                val names: (List<Command>) -> String = { cmd -> cmd.joinToString(", ") { "`${it.name}`" }}
                val mapNames: (Map<Long, Command>) -> String = { cmd ->
                    names(cmd.entries.map { it.value })
                }
                action {
                    val chatCommandsStr = names(chatCommandsRegistry.commands)
                    val applicationCommands = applicationCommands as DefaultApplicationCommandRegistry
                    val slashCommandsStr = mapNames(applicationCommands.slashCommands)
                    val userCommandsStr = mapNames(applicationCommands.userCommands)
                    val messageCommandsStr = mapNames(applicationCommands.messageCommands)

                    channel.createEmbed {
                        title = "help"
                        description = """
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

    private fun <T : Command> Map<Long, T>.firstMatch(argString: String): T? {
        return entries.map { it.value }.firstOrNull { argString.startsWith(it.name) }
    }

    private fun getCommandHelp(argString: String): String {
        val applicationCommands = applicationCommands as DefaultApplicationCommandRegistry
        val chatCommand = chatCommandsRegistry.commands.firstOrNull {
            argString.startsWith(it.name)
        }
        val slashCommand by lazy { applicationCommands.slashCommands.firstMatch(argString) }
        val userCommand by lazy { applicationCommands.userCommands.firstMatch(argString) }
        val messageCommand by lazy { applicationCommands.messageCommands.firstMatch(argString) }
        val fullHelp = when {
            chatCommand != null -> {
                var sb = "Syntax: ${chatCommand.name} ${getSyntax(chatCommand.arguments)}"
                sb += if (chatCommand.aliases.isNotEmpty()) {
                    "\nAliases: ${chatCommand.aliases.joinToString(" ") { "`$it`" }}"
                } else ""
                sb += "\nDesc: ${chatCommand.description}"
                sb += getArgHelp(chatCommand.arguments)
                sb
            }
            slashCommand != null -> {
                val cmd = slashCommand!!
                var sb = "Syntax: `${cmd.name} ${getSyntax(cmd.arguments)}`" +
                        "\nDesc: ${cmd.description}"
                sb += getArgHelp(cmd.arguments)
                sb
            }
            userCommand != null -> {
                val cmd = userCommand!!
                "Syntax: ${cmd.name}".trimIndent()
            }
            messageCommand != null -> {
                val cmd = messageCommand!!
                "Syntax: ${cmd.name}".trimIndent()
            }
            else -> {
                "no command"
            }
        }

        return fullHelp
    }

    private fun getArgHelp(arguments: (() -> Arguments)?): String {
        val args = arguments?.let { it() }?.args ?: return ""
        if (args.isEmpty()) return ""
        val argHelp = args.joinToString("\n") {
            "`${it.displayName}` ${it.description}"
        }
        return "\n**Args**\n$argHelp"
    }

    private fun getSyntax(arguments: (() -> Arguments)?): String {
        return arguments?.let { it() }?.args?.joinToString(" ") {
            if (it.converter is SingleToOptionalConverter<*>) "[${it.displayName}]"
            else "<${it.displayName}>"
        } ?: ""
    }

    inner class HelpArgs : Arguments() {
        val command = optionalString {
            name = "commandName"
            description = "name of a command you want help about"
        }
    }
}
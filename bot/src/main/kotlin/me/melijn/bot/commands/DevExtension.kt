package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.RootCommand
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.StringsUtil
import me.melijn.kordkommons.utils.StringUtils
import org.springframework.boot.ansi.AnsiColor

@KordExtension
class DevExtension : Extension() {

    override val name: String = "dev"

    override suspend fun setup() {
        publicSlashCommand {
            name = "test"
            description = "test"
            action {
                respond {
                    val names = getNames()

                    content = "called `${names.joinToString(" ")}`"
                }
            }
        }
        publicSlashCommand {
            name = "testsub"
            description = "test"
            publicSubCommand {
                name= "test"
                description = "testing"
                action {
                    respond {
                        val names = getNames()

                        content = "called `${names.joinToString(" ")}`"
                    }
                }
            }
        }
        publicSlashCommand {
            name = "testsubsub"
            description = "test"
            group("sub1") {
                description = "sin1"

                publicSubCommand {
                    name= "test"
                    description = "testing"
                    action {
                        respond {
                            val names = getNames()

                            content = "called `${names.joinToString(" ")}`"
                        }
                    }
                }
            }
        }
        chatCommand(::DumpMessageArgs) {
            name = "dump"
            description = "dump message content and embed"
            action {
                val linkArg = arguments.messageLink.parsed
                val message = if (linkArg != null) {
                    val parts = linkArg.split("/").takeLast(3).map { it.toULong() }
                    val guild = this@DevExtension.kord.getGuild(Snowflake(parts[0]))
                    val channel = guild?.getChannelOrNull(Snowflake(parts[1]))
                    if (channel == null || channel !is MessageChannel) {
                        this.channel.createMessage("that link is veeery stinky")
                        return@action
                    }
                    channel.getMessage(Snowflake(parts[2]))
                } else {
                    message.referencedMessage!!
                }
                val blue = StringsUtil.ansiFormat(AnsiColor.BLUE)
                val reset = StringsUtil.ansiFormat(AnsiColor.DEFAULT)
                var content = ""
                content += "${blue}content: ${reset}${message.content.replace("```", "'''")}\n"
                content += "${blue}embed: $reset"
                if (message.embeds.isNotEmpty()) {
                    content += "[\n" + message.embeds.joinToString(",\n") {
                        Json.encodeToString(
                            it.data
                        ).replace("```", "'''")
                    } + "\n]"
                }
                content += "\n"
                content += "${blue}attachments: ${reset}"
                if (message.attachments.isNotEmpty()) {
                    content += "[\n" + message.attachments.joinToString("\n") { it.filename + " - " + it.url } + "\n]"
                }
                content += "\n"
                paginator(targetChannel = channel) {
                    val parts = StringUtils.splitMessage(content)
                    for (part in parts) {
                        page {
                            title = "dumped ${message.id}"
                            description = "```ansi\n$part```"
                        }
                    }
                }.send()
            }
        }
    }

    private fun SlashCommandContext<*, *>.getNames(): List<String> {
       return buildList {
           when (val command = event.interaction.command) {
                is GroupCommand -> {
                    add(command.rootName)
                    add(command.groupName)
                    add(command.name)
                }
                is dev.kord.core.entity.interaction.SubCommand -> {
                    add(command.rootName)
                    add(command.name)
                }
                is RootCommand -> {
                    add(command.rootName)
                }
            }
        }
    }

    class DumpMessageArgs : Arguments() {
        val messageLink = optionalString {
            name = "messageLink"
            description = "discord formatted message link"

            validate {
                val a = value
                if (a != null) {
                    failIf("Not a normal message link!") { !".*/\\d+|@me/\\d+/\\d+".toRegex().matches(a) }
                } else {
                    val eventObj = context.eventObj
                    val ref = if (eventObj is MessageCreateEvent) {
                        eventObj.message.referencedMessage
                    } else null
                    failIf("stinkert!") { ref?.getJumpUrl() == null }
                }
            }
        }

        override fun validate() {
            messageLink.parsed
        }
    }
}

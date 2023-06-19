package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.MemManager
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.guildChatCommand
import me.melijn.gen.MemData
import org.koin.core.component.inject

@KordExtension
class MemExtension : Extension() {

    override val name: String = "mem"

    val memManager by inject<MemManager>()

    override suspend fun setup() {
        guildChatCommand(::MemeArguments) { // This is a command that can be used in public channels
            name = "mem"
            description = "Sends a random meme"
            aliases = arrayOf("m", "meme")

            action {
                val guildId = guild!!.idLong
                val meme = when (val name = arguments.name) {
                    "list" -> {
                        sendMemeList(guildId)
                        return@action
                    }

                    null -> memManager.getRandomMeme(guildId) ?: bail("No meme found")
                    else -> memManager.getRawById(guildId, name) ?: bail("`$name` does not exist.")
                }

                message.channel.createMessage {
                    content = meme.url
                }
            }
        }
        chatGroupCommand {
            name = "manageMems"
            description = "Manage memes"
            aliases = arrayOf("mm")

            this.chatCommand(::AddMemeArguments) {
                name = "add"
                description = "Add a meme"
                aliases = arrayOf("a")

                action {
                    val guildId = guild!!.idLong
                    val name = arguments.name
                    val memeUrl = arguments.meme ?: message.attachments.firstOrNull()?.url ?: bail("No meme provided")
                    memManager.store(MemData(guildId, name, memeUrl))
                    message.channel.createMessage("Added meme")
                }
            }

            this.chatCommand(::RemoveMemeArguments) {
                name = "remove"
                description = "Remove a meme"
                aliases = arrayOf("rm")
                action {
                    val guildId = guild!!.idLong
                    val name = arguments.name
                    memManager.deleteById(guildId, name)
                    message.channel.createMessage("Removed meme")
                }
            }

            this.chatCommand {
                name = "list"
                description = "Mem list"
                action {
                    val guildId = guild!!.idLong
                    sendMemeList(guildId)
                }
            }

            action {
                sendHelp()
            }
        }
    }

    context(ChatCommandContext<*>)
    private suspend fun sendMemeList(guildId: Long) {
        val memes = memManager.getByGuildIdx(guildId)
        paginator(targetChannel = this@ChatCommandContext.channel) {
            for (chunk in memes.chunked(20)) {
                this.page {
                    this.title = "Mems"
                    this.description = chunk.joinToString(" ") { "`${it.name}`" }
                }
            }
        }.send()
    }

    class AddMemeArguments : Arguments() {
        val name by string {
            name = "name"
            description = "meme name"
        }
        val meme by optionalString {
            name = "meme"
            description = "meme url or provide an attachment"
        }
    }

    class RemoveMemeArguments : Arguments() {
        val name by string {
            name = "name"
            description = "meme name"
        }
    }

    class MemeArguments : Arguments() {
        val name by optionalString {
            name = "name"
            description = "meme name"
        }
    }
}
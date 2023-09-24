package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import com.kotlindiscord.kord.extensions.pagination.PublicResponsePaginator
import com.kotlindiscord.kord.extensions.pagination.builders.PaginatorBuilder
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.FilterStrategy
import com.kotlindiscord.kord.extensions.utils.suggestStringCollection
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.MemManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.guildChatCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.respond
import me.melijn.gen.MemData
import org.koin.core.component.inject

@KordExtension
class MemExtension : Extension() {

    override val name: String = "mem"

    val memManager by inject<MemManager>()

    override suspend fun setup() {
        publicGuildSlashCommand(::MemeArguments) {
            name = "mem"
            description = "Sends a random meme"

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

                respond {
                    content = meme.url
                }
            }
        }
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

                respond {
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
                    respond("Added meme")
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
                    respond("Removed meme")
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

    context(PublicSlashCommandContext<*>)
    private suspend fun sendMemeList(guildId: Long) {
        val memes = memManager.getByGuildIdx(guildId)
        val locale = resolvedLocale.await()

        PublicResponsePaginator(PaginatorBuilder(locale, defaultGroup = "").apply {
            for (chunk in memes.chunked(20)) {
                this.page {
                    this.title = "Mems"
                    this.description = chunk.joinToString(" ") { "`${it.name}`" }
                }
            }
        }, interaction.hook).send()
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
            autoComplete {
                val memManager by inject<MemManager>()
                val guildId = guild!!.idLong
                val input = it.getOption("name")?.asString
                if (input == null) {
                    it.suggestStringCollection(emptyList())
                } else {
                    val memes = memManager.getByGuildIdx(guildId)
                    it.suggestStringCollection(memes.map { it.name }, FilterStrategy.Contains)
                }
            }
        }
    }
}
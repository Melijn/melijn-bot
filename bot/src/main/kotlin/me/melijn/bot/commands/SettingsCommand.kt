package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.rest.builder.message.create.embed
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.PrefixManager
import me.melijn.bot.utils.KordExUtils.intRange
import me.melijn.bot.utils.KordExUtils.stringLength
import me.melijn.gen.PrefixesData
import org.koin.core.component.inject

@KordExtension
class SettingsCommand : Extension() {

    override val name: String = "settings"
    val prefixManager: PrefixManager by inject()

    override suspend fun setup() {
        publicSlashCommand {
            name = "settings"
            description = "Setting SlashCommands"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
            }

            group("prefixes") {
                description = "prefix commands"
                publicSubCommand(::PrefixArg) {
                    name = "add"
                    description = "add a prefix for chatCommands"

                    action {
                        val guild = guild!!
                        val existingPrefixes = prefixManager.getPrefixes(guild.id)
                        val prefix = arguments.prefix
                        if (existingPrefixes.any { it.prefix.equals(prefix, true) }) {
                            respond { content = "You can't add prefixes twice" }
                            return@action
                        }

                        prefixManager.store(PrefixesData(guild.id.value, prefix))

                        respond {
                            embed {
                                description = "Added `$prefix` as prefix"
                            }
                        }
                    }
                }
                publicSubCommand {
                    name = "list"
                    description = "lists prefixes"

                    action {
                        val guild = guild!!
                        val prefixes = prefixManager.getPrefixes(guild.id).withIndex()

                        respond {
                            content = "```INI\n" +
                                prefixes.joinToString("\n") { "${it.index} - [${it.value.prefix}]" } +
                                "```"
                        }
                    }
                }
                publicSubCommand(::GuildPrefixRemoveArg) {
                    name = "remove"
                    description = "removes a prefix"

                    action {
                        val guild = guild!!
                        val prefixArg = prefixManager.getPrefixes(guild.id).withIndex()
                            .firstOrNull { it.value.prefix == arguments.prefix || it.index == arguments.index }
                        if (prefixArg == null) {
                            respond { content = "Race condition, try again" }
                            return@action
                        }
                        prefixManager.delete(prefixArg.value)
                        respond { content = "Deleted `${prefixArg.value.prefix}` from the server prefixes" }
                    }
                }
            }
        }
    }

    inner class GuildPrefixRemoveArg : Arguments() {
        val prefix by optionalString {
            name = "prefix"
            description = "an existing prefix"
            validate {
                failIf(index == null && value == null, "You must supply a prefix or index")

                value ?: return@validate
                stringLength(name, 1, 32)
                val prefixes = prefixManager.getPrefixes(context.getGuild()!!.id)
                val prefixNotExists = prefixes.none { it.prefix == value }
                failIf(prefixNotExists, "$value is a non existent prefix")
            }
        }
        val index by optionalInt {
            name = "prefixIndex"
            description = "index of an existing prefix"
            validate {
                value ?: return@validate
                val prefixesAmount = prefixManager.getPrefixes(context.getGuild()!!.id).size
                intRange(name, 0, prefixesAmount - 1)
            }
        }
    }

    inner class PrefixArg : Arguments() {
        val prefix by string {
            name = "prefix"
            description = "a prefix for the bot"
            validate {
                stringLength(name, 1, 32)
            }
        }
    }
}
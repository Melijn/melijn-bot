package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.color
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.common.entity.Permission
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.CommandEmbedColorManager
import me.melijn.bot.database.manager.PrefixManager
import me.melijn.bot.utils.ImageUtil
import me.melijn.bot.utils.ImageUtil.toInputStream
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.inRange
import me.melijn.bot.utils.KordExUtils.lengthBetween
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordUtil.toHex
import me.melijn.bot.utils.embedWithColor
import me.melijn.gen.CommandEmbedColorData
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

            group("embedcolor") {
                description = "embed color commands"
                publicSubCommand(::SetEmbedColorArgs) {
                    name = "set"
                    description = "sets the embedcolor in the provided scope"

                    action {
                        val scope = arguments.scope
                        val color = arguments.color
                        val colorManager by inject<CommandEmbedColorManager>()
                        val entityId = if (scope == Scope.PRIVATE) user.id else guild?.id ?: return@action
                        colorManager.store(CommandEmbedColorData(entityId.value, color.rgb))
                        respond {
                            embedWithColor {
                                title = tr("settings.embedColor.menuTitle")
                                description = tr("settings.embedColor.set", scope, color.toHex())
                                addSquareThumbnail(color)
                            }
                        }
                    }
                }
                publicSubCommand(::ViewEmbedColorArgs) {
                    name = "view"
                    description = "view the embedcolor from the provided scope"

                    action {
                        val scope = arguments.scope
                        val colorManager by inject<CommandEmbedColorManager>()
                        val entityId = if (scope == Scope.PRIVATE) user.id else guild?.id ?: return@action
                        val color = colorManager.getColor(entityId)
                        respond {
                            embedWithColor {
                                title = tr("settings.embedColor.menuTitle")
                                description = tr("settings.embedColor.view", scope, color.toHex())
                                addSquareThumbnail(color)
                            }
                        }
                    }
                }
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

    context(FollowupMessageCreateBuilder, EmbedBuilder)
    private fun addSquareThumbnail(color: Color?) {
        color?.let {
            val ins = ImageUtil.createSquare(64, it).toInputStream()
            val file = this@FollowupMessageCreateBuilder.addFile("file.png", ins)
            this@EmbedBuilder.thumbnail {
                url = "attachment://${file.name}"
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
                lengthBetween(name, 1, 32)
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
                inRange(name, 0, prefixesAmount - 1)
            }
        }
    }

    inner class PrefixArg : Arguments() {

        val prefix by string {
            name = "prefix"
            description = "a prefix for the bot"
            validate {
                lengthBetween(name, 1, 32)
            }
        }
    }

    inner class SetEmbedColorArgs : Arguments() {

        val scope by enumChoice<Scope> {
            name = "scope"
            description = "sets the embed color for the server or only for you"
            typeName = "scope"
        }

        val color by color {
            name = "color"
            description = "color for the embed"
        }
    }

    inner class ViewEmbedColorArgs : Arguments() {
        val scope by enumChoice<Scope> {
            name = "scope"
            description = "views the embed color from server or your settings"
            typeName = "scope"
        }

    }

    enum class Scope : InferredChoiceEnum {
        SERVER,
        PRIVATE
    }
}
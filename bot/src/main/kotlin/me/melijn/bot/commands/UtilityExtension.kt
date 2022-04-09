package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.snowflake
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicUserCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.canInteract
import com.kotlindiscord.kord.extensions.utils.selfMember
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.count
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordUtil.effectiveAvatarUrl
import org.koin.core.component.inject


@KordExtension
class UtilityExtension : Extension() {

    override val name: String = "utility"

    override suspend fun setup() {
        publicSlashCommand(::AvatarArgs) {
            name = "avatar"
            description = "Shows avatar"
            action {
                val target = (arguments.target.parsed ?: this.user).asUser()
                respond {
                    avatarEmbed(translationsProvider, target)
                }
            }
        }
        publicUserCommand {
            name = "avatar"
            action {
                val target = this.targetUsers.first()
                respond {
                    avatarEmbed(translationsProvider, target)
                }
            }
        }

        publicSlashCommand(::RoleInfoArgs) {
            name = "roleInfo"
            description = "gives roleInfo"
            action {
                val role = arguments.role.parsed
                val hex = java.lang.String.format("#%02x%02x%02x", role.color.red, role.color.green, role.color.blue)
                respond {
                    embed {
                        title = tr("roleInfo.infoTitle")
                        description = tr(
                            "roleInfo.infoDescription", role.name, role.id,
                            role.id.timestamp.toMessageFormat(DiscordTimestampStyle.ShortDateTime), role.rawPosition,
                            role.guild.roles.count(), role.mentionable, role.hoisted, role.managed,
                            role.color.rgb.toString(), hex.uppercase(),
                            "RGB(${role.color.red}, ${role.color.green}, ${role.color.blue})",
                            role.guild.selfMember().canInteract(role)
                        )
                        color = role.color
                        role.icon?.url?.let {
                            thumbnail { url = it }
                        }
                    }
                }
            }
        }

        publicSlashCommand(::IdInfoArgs) {
            name = "idInfo"
            description = "Shows timestamp"
            action {
                val id = this.arguments.id.parsed
                val millis = id.timestamp.toEpochMilliseconds()
                respond {
                    content = tr(
                        "idInfo.info",
                        id.timestamp.toMessageFormat(DiscordTimestampStyle.ShortDateTime),
                        millis.toString()
                    )
                }
            }
        }

        val chatCommandsRegistry: ChatCommandRegistry by inject()
        val applicationCommands: ApplicationCommandRegistry by inject()

        publicSlashCommand {
            name = "info"
            description = "Bot information"
            action {
                respond {
                    embed {
                        thumbnail {
                            url =
                                "https://cdn.discordapp.com/avatars/368362411591204865/9326b331e0e42f185318bb305fdaa950.png"
                        }
                        field {
                            name = tr("info.aboutFieldTitle")
                            value = tr(
                                "info.aboutFieldValue", "ToxicMushroom#0001",
                                "https://discord.gg/tfQ9s7u", "https://melijn.com/invite", "https://melijn.com"
                            )
                        }
                        field {
                            name = tr("info.infoFieldTitle")

                            var cmdCount = 0
                            cmdCount += chatCommandsRegistry.commands.size
                            val applicationCommands =
                                applicationCommands as DefaultApplicationCommandRegistry
                            cmdCount += applicationCommands.slashCommands.size
                            cmdCount += applicationCommands.userCommands.size
                            cmdCount += applicationCommands.messageCommands.size
                            value = tr("info.infoFieldValue", "$cmdCount")
                        }
                        field {
                            name = tr("info.versionsFieldTitle")
                            value = tr(
                                "info.versionsFieldValue", System.getProperty("java.version"),
                                "${KotlinVersion.CURRENT.major}.${KotlinVersion.CURRENT.minor}.${KotlinVersion.CURRENT.patch}"
                            )
                        }
                    }
                }
            }
        }
        publicSlashCommand {
            name = "ping"
            description = "bot latency"
            val kord = kord
            action {
                val timeStamp1 = System.currentTimeMillis()
                val msg = respond {
                    embed {
                        title = tr("ping.title")
                        description = tr("ping.gatewayPing", kord.gateway.averagePing?.inWholeMilliseconds ?: 0)
                    }
                }
                val timeStamp2 = System.currentTimeMillis()
                val sendMessagePing = timeStamp2 - timeStamp1
                val edited = msg.edit {
                    embed {
                        title = tr("ping.title")
                        description = msg.message.embeds.first().description + "\n" +
                                tr("ping.sendMessagePing", sendMessagePing)
                    }
                }
                val timeStamp3 = System.currentTimeMillis()
                val editMessagePing = timeStamp3 - timeStamp2
                msg.edit {
                    embed {
                        title = tr("ping.title")
                        description = edited.message.embeds.first().description + "\n" +
                                tr("ping.editMessagePing", editMessagePing)
                    }
                }
            }
        }
    }

    private fun FollowupMessageCreateBuilder.avatarEmbed(translationsProvider: TranslationsProvider, target: User) {
        embed {
            title = translationsProvider.tr("avatar.title", target.tag)
            description = translationsProvider.tr(
                "avatar.description", " **" +
                        "[direct](${target.effectiveAvatarUrl()}) • " +
                        "[x64](${target.effectiveAvatarUrl()}?size=64) • " +
                        "[x128](${target.effectiveAvatarUrl()}?size=128) • " +
                        "[x256](${target.effectiveAvatarUrl()}?size=256) • " +
                        "[x512](${target.effectiveAvatarUrl()}?size=512) • " +
                        "[x1024](${target.effectiveAvatarUrl()}?size=1024) • " +
                        "[x2048](${target.effectiveAvatarUrl()}?size=2048)**"
            )
            image = target.effectiveAvatarUrl() + "?size=2048"
        }
    }

    inner class AvatarArgs : Arguments() {
        val target = optionalUser {
            name = "user"
            description = "Gives the avatar of the user"
        }
    }

    inner class IdInfoArgs : Arguments() {
        val id = snowflake {
            name = "id"
            description = "id"
        }
    }

    inner class RoleInfoArgs : Arguments() {
        val role = role {
            name = "role"
            description = "gives information about the role"
        }
    }

}
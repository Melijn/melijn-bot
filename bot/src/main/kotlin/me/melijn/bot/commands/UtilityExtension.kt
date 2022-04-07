package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.snowflake
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicUserCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
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

        publicSlashCommand(::IdInfoArgs) {
            name = "idInfo"
            description = "Shows timestamp"
            action {
                val id = this.arguments.id.parsed
                val millis = id.timestamp.toEpochMilliseconds()
                respond {
                    content = tr("idInfo.info", id.timestamp.toMessageFormat(DiscordTimestampStyle.ShortDateTime), millis.toString())
                }
            }
        }

        val chatCommandsRegistry: ChatCommandRegistry by inject()
        val applicationCommands: ApplicationCommandRegistry by inject()

        publicSlashCommand() {
            name = "info"
            description = "Bot information"
            action {
                respond {
                    embed {
                        thumbnail {
                            url = "https://cdn.discordapp.com/avatars/368362411591204865/9326b331e0e42f185318bb305fdaa950.png"
                        }
                        field {
                            name = tr("info.aboutFieldTitle")
                            value = tr("info.aboutFieldValue", "ToxicMushroom#0001",
                                "https://discord.gg/tfQ9s7u", "https://melijn.com/invite", "https://melijn.com")
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
                            value = tr("info.versionsFieldValue", System.getProperty("java.version"),
                                "${KotlinVersion.CURRENT.major}.${KotlinVersion.CURRENT.minor}.${KotlinVersion.CURRENT.patch}")
                        }
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
            description ="id"
        }
    }
}
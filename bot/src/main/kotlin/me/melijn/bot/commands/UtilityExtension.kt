package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicUserCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordUtil.effectiveAvatarUrl

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
}
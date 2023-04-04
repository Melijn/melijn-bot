package me.melijn.bot.commands.administration

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.coroutines.await
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.JDAUtil.asTag
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import net.dv8tion.jda.api.Permission

@KordExtension
class RoleExtension : Extension() {

    override val name: String = "administration"

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "role"
            description = "role command"
            publicSubCommand(::RoleArgs) {
                name = "give"
                description = "gives role"
                check {
                    requireBotPermissions(Permission.MANAGE_ROLES)
                }
                action {
                    val role = arguments.role
                    val target = arguments.member

                    guild?.addRoleToMember(target, role)?.reason( "(role give) ${user.asTag}")?.await()
                    respond {
                        content = tr("role.give.gaveRole", role.asMention, target.asTag)
                    }
                }
            }
            publicSubCommand(::RoleArgs) {
                name = "take"
                description = "takes role"
                check {
                    requireBotPermissions(Permission.MANAGE_ROLES)
                }
                action {
                    val role = arguments.role
                    val target = arguments.member

                    guild?.removeRoleFromMember(target, role)?.reason( "(role take) ${user.asTag}")?.await()
                    respond {
                        content = tr("role.take.tookRole", role.asMention, target.asTag)
                    }
                }
            }
        }
    }
    inner class RoleArgs : Arguments() {
        val role by role {
            name = "role"
            description = "role"
        }

        val member by member {
            name = "member"
            description = "member"
            validate {
                failIf(translations.tr("botCannotInteractWithRole", context.resolvedLocale.await(), role.asMention)) {
                    !context.guild!!.selfMember.canInteract(role)
                }
            }
        }
    }
}
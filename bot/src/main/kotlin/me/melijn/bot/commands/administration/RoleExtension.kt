package me.melijn.bot.commands.administration

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.canInteract
import com.kotlindiscord.kord.extensions.utils.selfMember
import dev.kord.common.entity.Permission
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.tr

@KordExtension
class RoleExtension : Extension() {

    override val name: String = "administration"

    override suspend fun setup() {
        publicSlashCommand {
            name = "role"
            description = "role command"
            publicSubCommand(::RoleArgs) {
                name = "give"
                description = "gives role"
                check {
                    requireBotPermissions(Permission.ManageRoles)
                }
                action {
                    val role = arguments.role.parsed

                    val target = arguments.member.parsed
                    target.addRole(role.id, "(role give) ${user.asUser().tag}")
                    respond {
                        content = tr("role.give.gaveRole", role.mention, target.tag)
                    }
                }
            }
            publicSubCommand(::RoleArgs) {
                name = "take"
                description = "takes role"
                check {
                    requireBotPermissions(Permission.ManageRoles)
                }
                action {
                    val role = arguments.role.parsed

                    val target = arguments.member.parsed
                    target.removeRole(role.id, "(role take) ${user.asUser().tag}")
                    respond {
                        content = tr("role.take.tookRole", role.mention, target.tag)
                    }
                }
            }
        }
    }
    inner class RoleArgs : Arguments() {
        val role = role {
            name = "role"
            description = "role"
        }

        val member = member {
            name = "member"
            description = "member"
            validate {
                failIf(translations.tr("botCannotInteractWithRole", role.parsed.mention)) {
                    !context.getGuild()!!.selfMember().canInteract(role.parsed)
                }
            }
        }
    }
}
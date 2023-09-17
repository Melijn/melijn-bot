package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.XPManager
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSubCommand
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.image.ImageUtil.download
import me.melijn.gen.LevelRolesData
import me.melijn.gen.TopRolesData
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.utils.AttachedFile
import org.koin.core.component.inject
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.roundToInt

const val LEVEL_LOG_BASE = 1.2

@KordExtension
class LevelingExtension : Extension() {

    override val name: String = "leveling"
    val xpManager by inject<XPManager>()

    companion object {
        fun getLevel(xp: Long, base: Double): Long {
            val level = log((xp + 50).toDouble(), base)
            return floor(level).toLong() - 21
        }
    }

    override suspend fun setup() {
        publicGuildSlashCommand(::XPArgs) {
            name = "xp"
            description = "xp"

            action {
                val target = arguments.member ?: member!!
                val xp = xpManager.getGlobalXP(target)
                val guildXp = guild?.let { xpManager.getGuildXP(it, target) }
                respond {
                    val bufferedImage = LevelingExtension::class.java.getResourceAsStream("/slice2.png").use {
                        ImmutableImage.wrapAwt(ImageIO.read(it))
                    }.awt()
                    val bars = drawXpCard(bufferedImage, xp, guildXp, target)

                    val baos = ByteArrayOutputStream()
                    ImageIO.write(bars, "png", baos)
                    val bais = ByteArrayInputStream(baos.toByteArray())

                    files += AttachedFile.fromData(bais, "file.png")
                }
            }
        }
        publicSlashCommand(::SetXPArgs) {
            name = "setxp"
            description = "gives xp"
            check {
                userIsOwner()
            }
            action {
                val xp = arguments.xp.parsed
                val user = arguments.user.parsed

                xpManager.setGlobalXP(user, xp)
                guild?.let { xpManager.setGuildXP(it, user, xp) }

                respond {
                    content = "${user.effectiveName} xp: $xp"
                }
            }
        }
        publicGuildSlashCommand {
            name = "toproles"
            description = "You can choose a role to be put on a number of highest ranking users"

            publicGuildSubCommand(::TopRolesRemoveArgs) {
                name = "remove"
                description = "Removes a top role"

                action {

                    xpManager.topRolesManager.deleteByIndex1(
                        guild!!.idLong,
                        arguments.role.idLong
                    )
                    respond {
                        content = "Removed topRole: ${arguments.role.asMention}"
                    }
                }
            }
            publicGuildSubCommand(::TopRolesAddArgs) {
                name = "set"
                description = "Sets a top role"
                check {
                    requireBotPermissions(Permission.MANAGE_ROLES)
                    requirePermission(Permission.MANAGE_ROLES)
                }
                action {
                    val topRole = arguments.role
                    val level = arguments.level
                    val memberCount = arguments.memberCount

                    xpManager.topRolesManager.store(
                        TopRolesData(
                            guild!!.idLong,
                            memberCount,
                            topRole.idLong,
                            level
                        )
                    )

                    respond {
                        content = "Set ${topRole.asMention} as topRole for the highest **${memberCount}** members above level `$level`"
                    }
                }
            }
            publicGuildSubCommand {
                name = "list"
                description = "Call upon all the topRoles you have set"

                check {
                    requirePermission(Permission.MANAGE_ROLES)
                    requireBotPermissions(Permission.MANAGE_ROLES)
                }
                action {
                    val guild = guild!!
                    val topRoles = xpManager.topRolesManager.getByIndex0(guild.idLong)
                    respond {
                        content = if (topRoles.isEmpty()) {
                            "You don't have any topRoles set"
                        } else {
                            topRoles.joinToString("\n", prefix = "**Role - MinLevel - Number** \n") {
                                "<@&${it.roleId}> - ${it.minLevelTop} - ${it.memberCount}"
                            }
                        }
                    }
                }
            }
        }

        publicGuildSlashCommand {
            name = "levelroles"
            description = "You can set a role to a certain level"

            publicGuildSubCommand(::LevelRolesRemoveArgs) {
                name = "remove"
                description = "Removes a level role"

                action {
                    xpManager.levelRolesManager.delete(
                        LevelRolesData(
                            guild!!.idLong,
                            arguments.level,
                            arguments.role.idLong,
                            arguments.stay
                        )
                    )
                    respond {
                        content = "Removed levelRole: ${arguments.role.asMention} with the level ${arguments.level}"
                    }
                }
            }
            publicGuildSubCommand(::LevelRolesAddArgs) {
                name = "set"
                description = "Sets a level role"
                check {
                    requireBotPermissions(Permission.MANAGE_ROLES)
                    requirePermission(Permission.MANAGE_ROLES)
                }
                action {
                    val levelRole = arguments.role
                    val stay = arguments.stay
                    val level = arguments.level

                    xpManager.levelRolesManager.store(
                        LevelRolesData(
                            guild!!.idLong,
                            level,
                            levelRole.idLong,
                            stay
                        )
                    )

                    respond {
                        content = "Set ${levelRole.asMention} as the level $level levelRole"
                    }
                }
            }
            publicGuildSubCommand() {
                name = "list"
                description = "Call upon all the levelRoles you have set"
                check {
                    requirePermission(Permission.MANAGE_ROLES)
                    requireBotPermissions(Permission.MANAGE_ROLES)
                }
                action {
                    val guild = guild!!
                    val levelRoles = xpManager.levelRolesManager.getByIndex0(guild.idLong)
                    respond {
                        if (levelRoles.isEmpty()) {
                            content = "You don't have any levelRoles set"
                        } else {
                            content = levelRoles.joinToString("\n", prefix = "**Role - Level - Stay** \n") {

                                "<@&${it.roleId}> - ${it.level} - ${it.stay}"
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun PublicSlashCommandContext<XPArgs>.drawXpCard(
        bufferedImage: BufferedImage,
        xp: Long,
        guildXp: Long?,
        target: Member
    ): BufferedImage {
        val graphics = bufferedImage.createGraphics()
        val user = target

        /** Draw avatar **/
        val avatarData =
            download(user.effectiveAvatarUrl)
        val avatarImg = ImmutableImage.loader().fromBytes(avatarData).awt()
        graphics.drawImage(avatarImg, 56, 176, 408, 408, null)

        /** Draw username **/
        val arial = LevelingExtension::class.java.getResourceAsStream("/arial.ttf")
            .use { Font.createFont(Font.TRUETYPE_FONT, it) }
        graphics.font = arial.deriveFont(90f)
        graphics.paint = Color.decode("#BABABA")
        val rh = RenderingHints(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        )
        graphics.setRenderingHints(rh)
        graphics.drawString(user.effectiveName, 174, 140)
        graphics.dispose()

        val bar1 = drawXPBar(250, xp, bufferedImage)
        val bar2 = guildXp?.let { drawXPBar(470, it, bar1) } ?: bar1

        return bar2
    }

    private fun drawXPBar(
        y: Int,
        xp: Long,
        bufferedImage: BufferedImage
    ): BufferedImage {
        /** Draw XP text **/
        val graphics = bufferedImage.createGraphics()
        val arial = LevelingExtension::class.java.getResourceAsStream("/arial.ttf")
            .use { Font.createFont(Font.TRUETYPE_FONT, it) }
        graphics.font = arial.deriveFont(50f)
        graphics.paint = Color.decode("#BABABA")
        val rh = RenderingHints(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        )
        graphics.setRenderingHints(rh)

        var level = getLevel(xp, LEVEL_LOG_BASE)
        var xpLower: Long
        var xpUpper: Long
        if (level == 0L) {
            xpLower = 0
            xpUpper = 5
        } else {
            xpLower = floor(LEVEL_LOG_BASE.pow((level + 21).toDouble()) - 50).toLong()
            xpUpper = floor(LEVEL_LOG_BASE.pow((level + 22).toDouble()) - 50).toLong()
        }
        /** Relative xp to level variables **/
        var progressToNextLevel = xp - xpLower
        var nextLevelThreshold = xpUpper - xpLower
        if (nextLevelThreshold == progressToNextLevel) {
            level++
            xpLower = floor(LEVEL_LOG_BASE.pow((level + 21).toDouble()) - 50).toLong()
            xpUpper = floor(LEVEL_LOG_BASE.pow((level + 22).toDouble()) - 50).toLong()
            progressToNextLevel = xp - xpLower
            nextLevelThreshold = xpUpper - xpLower
        }

        val text = "$progressToNextLevel/$nextLevelThreshold XP | Level: $level"
        val textWidth = graphics.fontMetrics.stringWidth(text)
        graphics.drawString(text, 1586 - textWidth, y - 20)
        graphics.dispose()

        /** Draw XP bars **/
        val bars = BufferedImage(bufferedImage.width, bufferedImage.height, bufferedImage.type)
        val barGraphics = bars.createGraphics()
        barGraphics.paint = Color.decode("#142235")
        val percente = progressToNextLevel.toDouble() / nextLevelThreshold.toDouble()
        val end = (percente * 956).roundToInt()
        barGraphics.fillRect(645, y, end, 120)
        barGraphics.paint = Color.decode("#635C5C")
        barGraphics.fillRect(645 + end, y, (956 - end), 120)
        barGraphics.drawImage(bufferedImage, 0, 0, null)
        barGraphics.dispose()
        return bars
    }

    inner class SetXPArgs : Arguments() {
        val user = user {
            name = "user"
            description = "user"
        }
        val xp = long {
            name = "xp"
            description = "Sets xp lol"
        }
    }

    inner class XPArgs : Arguments() {
        val member by optionalMember {
            name = "member"
            description = "member"
        }
    }

    inner class LevelRolesAddArgs : Arguments() {
        val level by long {
            name = "level"
            description = "The level requirement at which you want to give a role"
        }
        val role by role {
            name = "role"
            description = "The role you want to give when you achieve a certain level"
        }
        val stay by boolean {
            name = "stay"
            description = "Role stays when you get the next level role"
        }
    }

    inner class LevelRolesRemoveArgs : Arguments() {
        val level by long {
            name = "level"
            description = "The level of the levelRole you want to remove"
        }
        val role by role {
            name = "role"
            description = "The role that you have set in the levelRole you want to remove"
        }
        val stay by boolean {
            name = "stay"
            description = "Role stays when you get the next level role"
        }
    }

    inner class TopRolesAddArgs : Arguments() {
        val level by long {
            name = "level"
            description = "The level requirement at which you want to give a role"
        }
        val role by role {
            name = "role"
            description = "The role you want to give when you achieve a certain level"
        }
        val memberCount by int {
            name = "member-count"
            description = "Number of highest members that can receive this role"
        }
    }

    inner class TopRolesRemoveArgs : Arguments() {
        val role by role {
            name = "role"
            description = "The role that you have set in the levelRole you want to remove"
        }
    }
}
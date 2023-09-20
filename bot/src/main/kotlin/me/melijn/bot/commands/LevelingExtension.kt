package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.AugmentedGuildXPData
import me.melijn.bot.database.manager.MissingUserManager
import me.melijn.bot.database.manager.XPManager
import me.melijn.bot.utils.*
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSubCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.image.ImageUtil.download
import me.melijn.gen.GuildXPData
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
import java.text.NumberFormat
import java.util.*
import javax.imageio.ImageIO
import javax.swing.text.NumberFormatter
import kotlin.math.*

const val LEVEL_LOG_BASE = 1.2
val bigNumberFormatter = NumberFormatter(NumberFormat.getInstance(Locale.UK))

@KordExtension
class LevelingExtension : Extension() {

    override val name: String = "leveling"
    val xpManager by inject<XPManager>()
    val missingUserManager by inject<MissingUserManager>()

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
        publicSlashCommand(::LeaderboardArgs) {
            name = "leaderboard"
            description = "Shows a leaderboard"
            action {
                val pageSize = 3
                val offset = ((arguments.page - 1L) * pageSize)
                val member = member
                val tableBuilder = tableBuilder {
                    header {
                        leftCell("#"); rightCell("Lvl"); rightCell("XP"); leftCell("user")
                    }
                    seperator(0, " ")
                }
                val addRow: suspend (Long, List<Long>, String) -> Unit = { position, bigNumbers, name ->
                    tableBuilder.row {
                        leftCell("${position}.")
                        for (bigNumber in bigNumbers) {
                            rightCell(bigNumberFormatter.valueToString(bigNumber))
                        }
                        leftCell(name.replace("_", "+"))
                    }
                }

                respond {
                    embedWithColor {

                        title = "Leaderboard"
                        when (arguments.options) {
                            LeaderboardOpt.GuildXP -> {
                                if (member == null) bail(tr("leaderboard.guildOnly"))
                                val guild = member.guild

                                val manager = xpManager.guildXPManager
                                val guildId = guild.idLong
                                val highestMemberLevelDatas = manager.getTop(guildId, pageSize, offset)
                                val rowCount = manager.rowCount(guildId)

                                fun xpAndLevel(entry: GuildXPData) = listOf(getLevel(entry.xp, LEVEL_LOG_BASE), entry.xp)
                                val addRequestedRows: suspend () -> Unit = {
                                    for ((i, entry) in highestMemberLevelDatas.withIndex()) {
                                        val name = if (entry.missing) {
                                            "missing"
                                        } else {
                                            val entryMember = guild.retrieveMemberById(entry.guildXPData.userId).awaitOrNull()
                                            if (entryMember == null) {
                                                missingUserManager.markMemberMissing(guildId, entry.guildXPData.userId)
                                                "missing"
                                            } else {
                                                entryMember.effectiveName
                                            }
                                        }
                                        addRow(i + offset + 1L, xpAndLevel(entry.guildXPData), name)
                                    }
                                }
                                if (!highestMemberLevelDatas.any { it.guildXPData.userId == user.idLong }) {
                                    val (invokerEntry, invokerPos, _) = manager.getPosition(guildId, user.idLong)
                                        ?: (AugmentedGuildXPData(GuildXPData(guildId, user.idLong, 0), rowCount, false))
                                    if (invokerPos < offset) {
                                        addRow(invokerPos, xpAndLevel(invokerEntry), member.effectiveName)
                                        tableBuilder.addSplit()
                                        addRequestedRows()
                                    } else {
                                        addRequestedRows()
                                        tableBuilder.addSplit()
                                        addRow(invokerPos, xpAndLevel(invokerEntry), member.effectiveName)
                                    }
                                } else {
                                    addRequestedRows()
                                }

                                val msgs = tableBuilder.build(true).first()
                                description = msgs

                                val totalPageCount = ceil(rowCount / pageSize.toFloat()).toLong()
                                footer("Page ${arguments.page}/$totalPageCount")
                            }

                            LeaderboardOpt.GlobalXP -> {
//                                val manager = xpManager.globalXPManager
//                                val highestUserLevelDatas = manager.getTop(pageSize, offset)
//                                val rowCount = manager.rowCount()
//
//                                fun xpAndLevel(entry: GlobalXPData) = listOf(getLevel(entry.xp, LEVEL_LOG_BASE), entry.xp)
//                                val addRequestedRows: suspend () -> Unit = {
//                                    for ((i, entry) in highestUserLevelDatas.withIndex()) {
//                                        val name = if (entry.missing) {
//                                            "missing"
//                                        } else {
//                                            val entryUser = shardManager.retrieveUserById(entry.userId).awaitOrNull()
//                                            if (entryUser == null) {
//                                                xpManager.markUserMissing(entry)
//                                                "missing"
//                                            } else {
//                                                entryUser.effectiveName
//                                            }
//                                        }
//                                        addRow(i + offset + 1L, xpAndLevel(entry), name)
//                                    }
//                                }
//                                if (!highestUserLevelDatas.any { it.userId == user.idLong }) {
//                                    val (invokerEntry, invokerPos) = manager.getPosition(user.idLong)
//                                        ?: (GlobalXPData(user.idLong, 0, false) to rowCount)
//                                    if (invokerPos < offset) {
//                                        addRow(invokerPos, xpAndLevel(invokerEntry), user.effectiveName)
//                                        tableBuilder.addSplit()
//                                        addRequestedRows()
//                                    } else {
//                                        addRequestedRows()
//                                        tableBuilder.addSplit()
//                                        addRow(invokerPos, xpAndLevel(invokerEntry), user.effectiveName)
//                                    }
//                                } else {
//                                    addRequestedRows()
//                                }
//
//                                val msgs = tableBuilder.build(true).first()
//                                description = msgs
//
//                                val totalPageCount = ceil(rowCount / pageSize.toFloat()).toLong()
//                                footer("Page ${arguments.page}/$totalPageCount")
                            }

                            LeaderboardOpt.Currency -> {
//                                val richestUserDatas = balanceManager.getTop(10, offset)

                            }
                        }
                    }
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
                    val idToDelete = arguments.roleId?.id ?: arguments.role?.idLong ?: bail(tr("deleted.nothing"))
                    val deleted = xpManager.topRolesManager.deleteByIndex1(
                        guild!!.idLong,
                        idToDelete
                    )
                    if (deleted > 0) {
                        respond {
                            content = "Removed topRole: <@&${idToDelete}> (`${idToDelete}`)"
                        }
                    } else {
                        respond {
                            content = tr("deleted.nothing")
                        }
                    }
                }
            }
            publicGuildSubCommand(::TopRolesAddArgs) {
                name = "set"
                description = "Sets a top role"

                requireBotPermissions(Permission.MANAGE_ROLES)
                requirePermission(Permission.MANAGE_ROLES)

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
                        content =
                            "Set ${topRole.asMention} as topRole for the highest **${memberCount}** members above level `$level`"
                    }
                }
            }
            publicGuildSubCommand {
                name = "list"
                description = "Call upon all the topRoles you have set"

                requirePermission(Permission.MANAGE_ROLES)
                requireBotPermissions(Permission.MANAGE_ROLES)

                action {
                    val guild = guild!!
                    val topRoles = xpManager.topRolesManager.getByIndex0(guild.idLong)
                    respond {
                        content = if (topRoles.isEmpty()) {
                            "You don't have any topRoles set"
                        } else {
                            topRoles.joinToString("\n", prefix = "**Role - MinLevel - Number** \n") {
                                "<@&${it.roleId}> (`${it.roleId}`) - ${it.minLevelTop} - ${it.memberCount}"
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

                requireBotPermissions(Permission.MANAGE_ROLES)
                requirePermission(Permission.MANAGE_ROLES)

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
            publicGuildSubCommand {
                name = "list"
                description = "Call upon all the levelRoles you have set"

                requirePermission(Permission.MANAGE_ROLES)
                requireBotPermissions(Permission.MANAGE_ROLES)

                action {
                    val guild = guild!!
                    val levelRoles = xpManager.levelRolesManager.getByIndex0(guild.idLong)
                    respond {
                        content = if (levelRoles.isEmpty()) {
                            "You don't have any levelRoles set"
                        } else {
                            levelRoles.joinToString("\n", prefix = "**Role - Level - Stay** \n") {

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

    enum class LeaderboardOpt : InferredChoiceEnum {
        GuildXP, GlobalXP, Currency
    }

    inner class LeaderboardArgs : Arguments() {
        val options by enumChoice<LeaderboardOpt> {
            name = "option"
            description = "5"
            typeName = "5"
        }

        val page by defaultingInt {
            name = "page"
            description = "The leaderboard page you want to view"
            defaultValue = 1
            validate {
                atLeast(name, 1)
            }
        }
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
        val role by optionalRole {
            name = "role"
            description = "The role that you have set in the levelRole you want to remove"
        }
        val roleId by optionalSnowflake {
            name = "role-id"
            description = "Use this incase the role is deleted"
        }
    }
}
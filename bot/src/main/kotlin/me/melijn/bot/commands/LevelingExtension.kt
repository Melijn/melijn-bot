package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.BalanceManager
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
import me.melijn.bot.utils.image.ImageUtil
import me.melijn.bot.utils.image.ImageUtil.download
import me.melijn.gen.LevelRolesData
import me.melijn.gen.TopRolesData
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
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
    val balanceManager by inject<BalanceManager>()
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
            description = "Shows info about XP and Level"

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
                    ImageUtil.writeSafe(bars, "png", baos)
                    val bais = ByteArrayInputStream(baos.toByteArray())

                    files += AttachedFile.fromData(bais, "file.png")
                }
            }
        }
        publicSlashCommand(::LeaderboardArgs) {
            name = "leaderboard"
            description = "Shows a leaderboard"

            action {
                val pageSize = 10
                val offset = ((arguments.page - 1L) * pageSize)
                val invokerId = user.idLong
                val member = member

                var tableBuilder = tableBuilder {
                    header {
                        leftCell("#"); rightCell("Lvl"); rightCell("XP"); leftCell("user")
                    }
                    seperator(0, " ")
                }

                val userNameFetcher: suspend (LeaderboardData) -> String = { lbData ->
                    getUserNameFor(lbData.userId, lbData.missing)
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

                val addRequestedRows: suspend (List<LeaderboardData>, suspend (LeaderboardData) -> String) -> Unit =
                    { lbData, nameFetcher ->
                        for ((i, entry) in lbData.withIndex()) {
                            val name = nameFetcher(entry)
                            addRow(i + offset + 1L, entry.dataList, name)
                        }
                    }

                suspend fun PublicSlashCommandContext<LeaderboardArgs>.addRequestedRowsWithSelf(
                    lbDatas: List<LeaderboardData>,
                    bigNumbersColumnCount: Int,
                    rowCount: Long,
                    invokerEntryFetcher: suspend () -> LeaderboardData?,
                    nameFetcher: suspend (LeaderboardData) -> String
                ) {
                    if (lbDatas.none { it.userId == invokerId }) {
                        val invokerLbData = invokerEntryFetcher()
                        val invokerPos = invokerLbData?.position ?: rowCount
                        val dataList = invokerLbData?.dataList ?: List(bigNumbersColumnCount) { 0L }
                        if (invokerPos < offset) {
                            addRow(invokerPos, dataList, user.effectiveName)
                            tableBuilder.addSplit()
                            addRequestedRows(lbDatas, nameFetcher)
                        } else {
                            addRequestedRows(lbDatas, nameFetcher)
                            tableBuilder.addSplit()
                            addRow(invokerPos, dataList, user.effectiveName)
                        }
                    } else {
                        addRequestedRows(lbDatas, nameFetcher)
                    }
                }

                respond {
                    embedWithColor {
                        title = "Leaderboard"

                        val rowCount = when (arguments.options) {
                            LeaderboardOpt.GuildXP -> {
                                title = "${tr("leaderboard.guildXP")} $title"
                                if (member == null) bail(tr("leaderboard.guildOnly"))
                                val guild = member.guild

                                val manager = xpManager.guildXPManager
                                val guildId = guild.idLong
                                val highestMemberLevelDatas = manager.getTop(guildId, pageSize, offset)
                                val rowCount = manager.rowCount(guildId)

                                val memberNameFetcher: suspend (LeaderboardData) -> String = { lbData ->
                                    guild.getUserNameFor(lbData.userId, lbData.missing)
                                }

                                val invokerEntryFetcher: suspend () -> LeaderboardData? =
                                    { manager.getPosition(guildId, invokerId) }
                                addRequestedRowsWithSelf(
                                    highestMemberLevelDatas, 2, rowCount, invokerEntryFetcher,
                                    memberNameFetcher
                                )

                                rowCount
                            }

                            LeaderboardOpt.GlobalXP -> {
                                title = "${tr("leaderboard.globalXP")} $title"
                                val manager = xpManager.globalXPManager
                                val highestUserLevelDatas = manager.getTop(pageSize, offset)
                                val rowCount = manager.rowCount()
                                val invokerEntryFetcher: suspend () -> LeaderboardData? =
                                    { manager.getPosition(invokerId) }
                                addRequestedRowsWithSelf(
                                    highestUserLevelDatas, 2, rowCount, invokerEntryFetcher,
                                    userNameFetcher
                                )

                                rowCount
                            }

                            LeaderboardOpt.Currency -> {
                                val currency = tr("currency")
                                title = "$currency $title"
                                tableBuilder = tableBuilder {
                                    header {
                                        leftCell("#"); rightCell(currency); leftCell("user")
                                    }
                                    seperator(0, " ")
                                }
                                val manager = balanceManager
                                val highestUserLevelDatas = manager.getTop(pageSize, offset)
                                val rowCount = manager.rowCount()
                                val invokerEntryFetcher: suspend () -> LeaderboardData? =
                                    { manager.getPosition(invokerId) }

                                addRequestedRowsWithSelf(
                                    highestUserLevelDatas, 1, rowCount, invokerEntryFetcher,
                                    userNameFetcher
                                )

                                rowCount
                            }
                        }

                        val msgs = tableBuilder.build(true).first()
                        description = msgs
                        val totalPageCount = ceil(rowCount / pageSize.toFloat()).toLong()
                        footer("Page ${arguments.page}/$totalPageCount")
                    }
                }
            }
        }

        publicGuildSlashCommand {
            name = "toproles"
            description = "Manages roles which are put on a number of highest server-level users"

            publicGuildSubCommand(::TopRolesRemoveArgs) {
                name = "remove"
                description = "Removes a top role"

                requireBotPermissions(Permission.MANAGE_ROLES)
                requirePermission(Permission.MANAGE_ROLES)

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
        chatCommand(::SetXPArgs) {
            name = "setxp"
            description = "gives xp"
            check {
                userIsOwner()
            }
            action {
                val newXP = arguments.xp
                val user = arguments.user

                val xp = if (newXP != null) {
                    xpManager.setGlobalXP(user, newXP)
                    guild?.let { xpManager.setGuildXP(it, user, newXP) }
                    newXP
                } else xpManager.getGlobalXP(user)

                channel.createMessage {
                    val stateText = "was changed to".takeIf { newXP != null } ?: "is"
                    content = "${user.effectiveName} their xp $stateText: `$xp`"
                }
            }
        }
        publicGuildSlashCommand {
            name = "levelroles"
            description = "Manages roles which are obtained by reaching a server-level threshold"

            publicGuildSubCommand(::LevelRolesRemoveArgs) {
                name = "remove"
                description = "Removes a level role"

                requireBotPermissions(Permission.MANAGE_ROLES)
                requirePermission(Permission.MANAGE_ROLES)

                action {
                    val levelRoleData = xpManager.levelRolesManager.getByIndex1(guild!!.idLong, arguments.level)
                    xpManager.levelRolesManager.deleteByIndex1(guild!!.idLong, arguments.level)

                    if (levelRoleData == null) {
                        respond { content = tr("deleted.nothing") }
                        return@action
                    }

                    respond {
                        content =
                            "Removed levelRole: <@&${levelRoleData.roleId}> with level requirement: ${arguments.level}"
                    }
                }
            }
            publicGuildSubCommand(::LevelRolesAddArgs) {
                name = "set"
                description = "Sets a level role"

                requirePermission(Permission.MANAGE_ROLES)
                requireBotPermissions(Permission.MANAGE_ROLES)

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

    private suspend fun Guild.getUserNameFor(userId: Long, missing: Boolean): String {
        val entryUser = this.takeUnless { missing }?.retrieveMemberById(userId)?.awaitOrNull()
        if (entryUser == null) missingUserManager.markMemberMissing(this.idLong, userId)
        return entryUser?.effectiveName ?: "missing"
    }

    private suspend fun getUserNameFor(userId: Long, missing: Boolean): String {
        val entryUser = shardManager.takeUnless { missing }?.retrieveUserById(userId)?.awaitOrNull()
        if (entryUser == null) missingUserManager.markUserDeleted(userId)
        return entryUser?.effectiveName ?: "missing"
    }

    private suspend fun drawXpCard(
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
            description = "The leaderboard type"
            typeName = "5"
        }

        val page by defaultingInt {
            name = "page"
            description = "The leaderboard page"
            defaultValue = 1
            validate {
                atLeast(name, 1)
            }
        }
    }

    inner class SetXPArgs : Arguments() {
        val user by user {
            name = "user"
            description = "user"
        }
        val xp by optionalLong {
            name = "xp"
            description = "xp amount to set [omit this arg to view the raw xp amount instead]"
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
            description = "The level threshold at which to give the role [You cannot have multiple roles per level]"
        }
        val role by role {
            name = "role"
            description = "The obtainable role"
        }
        val stay by boolean {
            name = "stay"
            description = "Whether the roles is kept when a user obtains the next level role"
        }
    }

    inner class LevelRolesRemoveArgs : Arguments() {
        val level by long {
            name = "level"
            description = "The level of the levelRole you want to remove"
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

interface LeaderboardData {
    val userId: Long
    val position: Long
    val missing: Boolean
    val dataList: List<Long>
}
package me.melijn.bot.commands.utils

import com.freya02.emojis.Emojis
import com.freya02.emojis.TwemojiType
import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralUserCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicUserCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageEdit
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.InvitesManager
import me.melijn.bot.database.manager.MemberJoinTrackingManager
import me.melijn.bot.database.manager.MissingUserManager
import me.melijn.bot.events.UserNameListener
import me.melijn.bot.utils.JDAUtil.toHex
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.StringsUtil
import me.melijn.bot.utils.StringsUtil.batchingJoinToString
import me.melijn.bot.utils.TimeUtil.format
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.utils.MarkdownUtil
import net.dv8tion.jda.api.utils.SplitUtil
import net.dv8tion.jda.api.utils.TimeFormat
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.koin.core.component.inject
import java.awt.Color
import java.util.*


@KordExtension
class UtilityExtension : Extension() {

    override val name: String = "utility"

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "cleansing"
            description = "Clears the dm blocks, missing member status, delete user status of you"

            action {
                val missingUsersManager by inject<MissingUserManager>()
                val member = member!!
                val guildId = member.guild.idLong
                val memberId = member.idLong
                missingUsersManager.markUserDmsOpen(memberId)
                missingUsersManager.markUserReinstated(memberId)
                missingUsersManager.markMemberPresent(guildId, memberId)
                respond {
                    content = "Cleansed"
                }
            }
        }

        publicGuildSlashCommand(::DevGuildArgs) {
            name = "roles"
            description = "Show server roles"

            action {
                val targetGuild = arguments.guild ?: this.guild!!

                val body = targetGuild.roles
                    .withIndex()
                    .joinToString("\n") { (i, role) ->
                        "$i - [${role.name}] - ${role.id}"
                    }
                val header = tr("rolesList.serverTitle", targetGuild.name)
                val parts = SplitUtil.split(body, 2000 - 10 - header.length, SplitUtil.Strategy.NEWLINE)
                    .map { "```INI\n$it```" }
                    .toMutableList()
                parts[0] = header + parts[0]

                for (part in parts) {
                    respond {
                        content = part
                    }
                }
            }
        }

        publicSlashCommand(::RawArgs) {
            name = "raw"
            description = "replies with raw input"
            action {
                respond { content = "```" + arguments.raw.replace("`", "‘") + "```" }
            }
        }

        publicSlashCommand(::AvatarArgs) {
            name = "avatar"
            description = "Shows avatar"
            action {
                val avatarUrl = (arguments.targetAvatar ?: this.user.effectiveAvatarUrl)
                val name = (arguments.targetName ?: this.user.effectiveName)

                respond {
                    avatarEmbed(translationsProvider, resolvedLocale.await(), name, avatarUrl)
                }
            }
        }
        publicUserCommand {
            name = "avatar"
            action {
                val avatarUrl = this.event.targetMember?.effectiveAvatarUrl ?: this.target.effectiveAvatarUrl
                val name = this.event.targetMember?.effectiveName ?: this.target.effectiveName
                respond {
                    avatarEmbed(translationsProvider, resolvedLocale.await(), name, avatarUrl)
                }
            }
        }

        publicGuildSlashCommand {
            name = "emotes"
            description = "Shows you all the server emotes"
            action {
                this.guild!!.emojis
                    .sortedBy { it.idLong }
                    .batchingJoinToString(2000, "\n") {
                        "${it.asMention} - (`${it.idLong}`) - ${it.name}"
                    }.takeIf { it.isNotEmpty() }?.forEach {
                        respond {
                            content = it
                        }
                    } ?: respond {
                    content = "No emojis"
                }
            }
        }

        publicSlashCommand(::RoleInfoArgs) {
            name = "roleinfo"
            description = "gives roleInfo"
            action {
                val role = arguments.role.parsed
                val roleColor = role.color ?: Color(Role.DEFAULT_COLOR_RAW)
                val hex = roleColor.toHex()
                respond {
                    embed {
                        title = tr("roleInfo.infoTitle")
                        description = tr(
                            "roleInfo.infoDescription", role.name, role.id,
                            TimeFormat.DATE_TIME_SHORT.format(role),
                            role.positionRaw,
                            role.guild.roles.count(), role.isMentionable, role.isHoisted, role.isManaged,
                            roleColor.rgb.toString(), hex.uppercase(),
                            "RGB(${roleColor.red}, ${roleColor.green}, ${roleColor.blue})",
                            role.guild.selfMember.canInteract(role)
                        )
                        color = roleColor.rgb

                        // Thumbnail
                        role.icon?.let {
                            val emoji = it.emoji
                            thumbnail = if (emoji != null) {
                                Emojis.ofUnicode(emoji).getTwemojiImageUrl(TwemojiType.X72)
                            } else {
                                it.iconUrl
                            }
                        }
                    }
                }
            }
        }

        publicSlashCommand(::UserInfoArgs) {
            name = "userinfo"
            description = "Gives info about user"

            check {
                anyGuild()
            }

            val memberJoinTrackingManager by KoinUtil.inject<MemberJoinTrackingManager>()
            val inviteManager by KoinUtil.inject<InvitesManager>()

            action {
                val user = arguments.user.parsed ?: this.user
                val guild = guild!!
                val member: Member? = runCatching { guild.retrieveMember(user).await() }.getOrNull()
                val isSupporter = false
                val profile = user.retrieveProfile().await()
                val voiceState = member?.voiceState
                val statusIconString = getStatusIcons(voiceState)
                val inviteInfo = memberJoinTrackingManager.getById(guild.idLong, user.idLong)

                respond {
                    embed {
                        description = tr(
                            "userInfo.userInfoSection", user.name, user.discriminator,
                            user.id, user.isBot, isSupporter, user.effectiveAvatarUrl,
                            profile.banner != null, profile.bannerUrl,
                            TimeFormat.DATE_TIME_SHORT.format(user),
                            user.flags.joinToString(separator = " ") { getBadge(it) },
                            user.globalName
                        )

                        if (member != null) {
                            val roleString = member.roles.toList()
                                .sortedBy { it.positionRaw }
                                .reversed()
                                .joinToString {
                                    it.asMention
                                }
                            description += tr(
                                "userInfo.memberInfoSection", roleString,
                                member.nickname,
                                member.isOwner,
                                TimeFormat.DATE_TIME_SHORT.format(member.timeJoined),
                                member.timeBoosted?.let { TimeFormat.DATE_TIME_SHORT.format(it) } ?: "",
                                voiceState?.inAudioChannel(), statusIconString, guild.selfMember.canInteract(member)
                            )
                        }

                        if (inviteInfo != null) {
                            val inviteData = inviteManager.getById(inviteInfo.inviteCode, guild.idLong)
                            val inviteInfoString = inviteData?.let { invite ->
                                val expiresString =
                                    invite.expiry?.let { TimeFormat.RELATIVE.format(invite.createdAt + it) }
                                        ?: "never"
                                "(`${invite.inviteCode}`, uses: **${invite.uses}**, expiry: ${expiresString})"
                            } ?: "`${inviteInfo.inviteCode}`"
                            description += tr(
                                "userInfo.inviteInfoSection",
                                inviteInfoString,
                                inviteData?.userId?.let { shardManager.getUserById(it)?.effectiveName?.let { "$it " } } + "(`${inviteData?.userId}`)",
                                TimeFormat.DATE_TIME_LONG.format(inviteInfo.firstJoinTime),
                                inviteInfo.joins
                            )
                        }
                        thumbnail = user.effectiveAvatarUrl
                    }
                }
            }
        }

        publicSlashCommand(::ServerInfoArgs) {
            name = "serverinfo"
            description = "gives information about the server"
            action {
                val guild =
                    arguments.serverId.parsed?.let { this@UtilityExtension.shardManager.getGuildById(it.id) }
                        ?: this.guild
                if (guild == null) {
                    respond { content = tr("serverInfo.guildOnlyOrArgumentPassed") }
                    return@action
                }
                val memberCount = guild.memberCount
                val userCount = guild.members.count { !it.user.isBot }
                val botCount = guild.members.count() - userCount
                val iconUrl = guild.iconUrl
                val bannerUrl = guild.bannerUrl
                val splashUrl = guild.splashUrl
                respond {
                    embed {
                        thumbnail = iconUrl.toString()
                        description = tr(
                            "serverInfo.responseDescription", guild.name, guild.id,
                            guild.owner?.effectiveName,
                            TimeFormat.DATE_TIME_SHORT.format(guild),
                            tr("serverVerificationLevel", guild.verificationLevel),
                            tr("serverMFATier", guild.requiredMFALevel),
                            tr("serverContentFilterLevel", guild.explicitContentLevel),
                            tr("serverNSFWLevel", guild.nsfwLevel),
                            true,
                            memberCount,
                            userCount, userCount.toFloat() / memberCount * 100,
                            botCount, botCount.toFloat() / memberCount * 100,
                            guild.boostCount,
                            tr("serverBoostTier", guild.boostTier),
                            guild.roles.size,
                            guild.textChannels.size,
                            guild.voiceChannels.size,
                            guild.categories.size,
                            iconUrl != null, iconUrl.toString(), bannerUrl != null, bannerUrl.toString(),
                            splashUrl != null, splashUrl.toString()
                        )
                    }
                }
            }
        }

        publicSlashCommand(::IdInfoArgs) {
            name = "idinfo"
            description = "Shows timestamp"
            action {
                val id = this.arguments.id

                respond {
                    content = tr(
                        "idInfo.info",
                        TimeFormat.DATE_TIME_SHORT.format(id.getTimeCreated()),
                        id.getTimeCreated().toInstant().toEpochMilli().toString()
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

                        thumbnail =
                            "https://cdn.discordapp.com/avatars/368362411591204865/9326b331e0e42f185318bb305fdaa950.png"

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
            action {
                val timeStamp1 = System.currentTimeMillis()
                val msg = respond {
                    embed {
                        title = tr("ping.title")
                        description = tr("ping.gatewayPing", shardManager.averageGatewayPing)
                    }
                }
                val timeStamp2 = System.currentTimeMillis()
                val sendMessagePing = timeStamp2 - timeStamp1
                val edited = msg.editMessage(MessageEdit {
                    embed {
                        title = tr("ping.title")
                        description = msg.embeds.first().description + "\n" +
                                tr("ping.sendMessagePing", sendMessagePing)
                    }
                }).await()
                val timeStamp3 = System.currentTimeMillis()
                val editMessagePing = timeStamp3 - timeStamp2
                msg.editMessage(MessageEdit {
                    embed {
                        title = tr("ping.title")
                        description = edited.embeds.first().description + "\n" +
                                tr("ping.editMessagePing", editMessagePing)
                    }
                }).await()
            }
        }

        publicSlashCommand(::RawArgs) {
            name = "normalize-unicode"
            description = "Normalize and strip garbage from unicode"
            action {
                respond {
                    var filtered = StringsUtil.filterGarbage(arguments.raw)
                    if (filtered.isBlank())
                        filtered = "\u200b" // Just send a message that _looks_ empty

                    content = filtered
                }
            }
        }

        ephemeralUserCommand {
            name = "Normalize name"

            requireBotPermissions(Permission.NICKNAME_CHANGE)
            requirePermission(Permission.NICKNAME_CHANGE)

            action {
                val guild = this.guild ?: bail("Must be executed in a guild")
                val target = guild.retrieveMember(this.target).await()

                val currentName = target.effectiveName
                val properName = StringsUtil.getNormalizedUsername(target)
                if (properName == currentName) {
                    respond {
                        content = tr("nameNormalization.fail.noSanitize")
                    }
                } else {
                    if (!guild.selfMember.canInteract(target))
                        bail(tr("nameNormalization.fail.noPermission"))

                    UserNameListener.fixName(target, properName)

                    respond {
                        content = tr(
                            "nameNormalization.success",
                            MarkdownUtil.monospace(currentName),
                            MarkdownUtil.monospace(properName)
                        )
                    }
                }
            }
        }
    }

    private fun getStatusIcons(voiceState: GuildVoiceState?): String {
        if (voiceState == null) {
            return ""
        }
        var list = ""
        if (voiceState.isSelfDeafened) list += " <:deafened:1092828247043084348>"
        if (voiceState.isDeafened) list += " <:server_deafened:1092828229947109576>"
        if (voiceState.isSelfMuted) list += " <:muted:1092828179372195920>"
        if (voiceState.isMuted) list += " <:server_muted:1092828203892097164>"
        if (voiceState.isSendingVideo) list += " <:video:1092828266127184096>"
        if (voiceState.isStream) list += " <:screen_share:1092828281310560327>"
        return list
    }

    private fun InlineMessage<MessageCreateData>.avatarEmbed(
        translationsProvider: TranslationsProvider,
        locale: Locale,
        targetName: String,
        targetAvatarUrl: String
    ) {
        embed {
            title = translationsProvider.tr("avatar.title", locale, targetName)
            description = translationsProvider.tr(
                "avatar.description", locale, " **" +
                        "[direct](${targetAvatarUrl}) • " +
                        "[x64](${targetAvatarUrl}?size=64) • " +
                        "[x128](${targetAvatarUrl}?size=128) • " +
                        "[x256](${targetAvatarUrl}?size=256) • " +
                        "[x512](${targetAvatarUrl}?size=512) • " +
                        "[x1024](${targetAvatarUrl}?size=1024) • " +
                        "[x2048](${targetAvatarUrl}?size=2048)**"
            )
            image = "$targetAvatarUrl?size=2048"
        }
    }

    inner class AvatarArgs : Arguments() {

        val target by optionalUser {
            name = "user"
            description = "Gives the avatar of the user"
        }
        val memberTarget by optionalMember {
            name = "member"
            description = "Gives the avatar of the member"
        }

        val targetAvatar by lazy { memberTarget?.effectiveAvatarUrl ?: target?.effectiveAvatarUrl }
        val targetName by lazy { memberTarget?.effectiveName ?: target?.effectiveName }
    }

    inner class ServerInfoArgs : Arguments() {

        val serverId = optionalSnowflake {
            name = "server-id"
            description = "Id of the server"
            validate {
                val betterValue = value ?: return@validate
                failIf(message = tr("arguments.guildId.noGuild")) {
                    shardManager.getGuildById(betterValue.id) == null
                }
            }
        }
    }

    inner class IdInfoArgs : Arguments() {

        val id by snowflake {
            name = "id"
            description = "id"
        }
    }

    inner class DevGuildArgs : Arguments() {

        val guild by optionalGuild {
            name = "guild"
            description = "Only devs may provide this argument"
            validate {
                if (this.value != null) userIsOwner()
            }
        }
    }

    inner class RawArgs : Arguments() {

        val raw by string {
            name = "raw"
            description = "raw input"
        }
    }

    inner class RoleInfoArgs : Arguments() {

        val role = role {
            name = "role"
            description = "gives information about the role"
        }
    }

    inner class UserInfoArgs : Arguments() {

        val user = optionalUser {
            name = "user"
            description = "A user"
        }
    }

    private fun getBadge(flag: User.UserFlag): String {
        return when (flag) {
            User.UserFlag.STAFF -> "<:furry:1092827519092273202>"
            User.UserFlag.PARTNER -> "<:partnered:1092827597139869826>"
            User.UserFlag.BUG_HUNTER_LEVEL_1 -> "<:bug_hunter:1092827225142853764>"
            User.UserFlag.BUG_HUNTER_LEVEL_2 -> "<:gold_bughunter:1092827412116537394>"
            User.UserFlag.HYPESQUAD -> "<:hypesquad_events_v1:1092827553330385030>"
            User.UserFlag.HYPESQUAD_BRAVERY -> "<:bravery:1092827191303221268>"
            User.UserFlag.HYPESQUAD_BRILLIANCE -> "<:brilliance:1092827209510686881>"
            User.UserFlag.HYPESQUAD_BALANCE -> "<:balance:1092827173687132171>"
            User.UserFlag.EARLY_SUPPORTER -> "<:early_supporter:1092827486267646063>"
            User.UserFlag.TEAM_USER -> "`team user`"
            User.UserFlag.VERIFIED_BOT -> "`verified bot`"
            User.UserFlag.VERIFIED_DEVELOPER -> "<:early_verified_developer:1092827503992778810>"
            User.UserFlag.CERTIFIED_MODERATOR -> "<:discordmod:1092828064410501161>"
            User.UserFlag.BOT_HTTP_INTERACTIONS -> "`http bot`"
            User.UserFlag.ACTIVE_DEVELOPER -> "<:activedeveloper:1092834795609935923>"
            User.UserFlag.UNKNOWN -> "`\uD83E\uDE78`"
        }
    }
}

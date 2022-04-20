package me.melijn.bot.commands

import com.freya02.emojis.Emojis
import com.freya02.emojis.TwemojiType
import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.application.DefaultApplicationCommandRegistry
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandRegistry
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalSnowflake
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
import dev.kord.common.entity.UserFlag
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.User
import dev.kord.core.entity.VoiceState
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordUtil.bannerUrl
import me.melijn.bot.utils.KordUtil.effectiveAvatarUrl
import me.melijn.bot.utils.KordUtil.iconUrl
import me.melijn.bot.utils.KordUtil.splashUrl
import org.koin.core.component.inject
import java.util.*


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
                    avatarEmbed(translationsProvider, getLocale(), target)
                }
            }
        }
        publicUserCommand {
            name = "avatar"
            action {
                val target = this.targetUsers.first()
                respond {
                    avatarEmbed(translationsProvider, getLocale(), target)
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
                        role.unicodeEmoji?.let {
                            thumbnail { url = Emojis.ofUnicode(it).getTwemojiImageUrl(TwemojiType.X72) }
                        }
                    }
                }
            }
        }

        publicSlashCommand(::UserInfoArgs) {
            name = "userInfo"
            description = "Gives info about user"

            check {
                anyGuild()
            }

            action {
                val user = arguments.user.parsed ?: this.user.asUser()
                val guild = guild!!
                val member = user.asMember(guild.id)
                val isSupporter = false
                val banner = user.withStrategy(EntitySupplyStrategy.rest).fetchUser().getBannerUrl(Image.Format.PNG)
                    ?.plus("?size=2048")
                val voiceState = member.getVoiceStateOrNull()
                val statusIconString = getStatusIcons(voiceState)
                respond {
                    embed {
                        description = tr("userInfo.userInfoSection", user.username, user.discriminator,
                            user.id, user.isBot, isSupporter, user.effectiveAvatarUrl(),
                            banner != null, banner.toString(),
                            user.id.timestamp.toMessageFormat(DiscordTimestampStyle.ShortDateTime),
                            user.publicFlags?.flags?.joinToString(separator = " ") { getBadge(it) } ?: "")
                        val roleString = member.roles.toList()
                            .sortedBy { it.rawPosition }
                            .reversed()
                            .joinToString {
                                it.mention
                            }
                        description += tr(
                            "userInfo.memberInfoSection", roleString,
                            member.nickname ?: "",
                            member.isOwner(),
                            member.joinedAt.toMessageFormat(DiscordTimestampStyle.ShortDateTime),
                            member.premiumSince?.toMessageFormat(DiscordTimestampStyle.ShortDateTime) ?: "",
                            voiceState?.channelId != null, statusIconString, guild.selfMember().canInteract(member)
                        )
                        thumbnail {
                            url = user.effectiveAvatarUrl()
                        }
                    }
                }
            }
        }

        publicSlashCommand(::ServerInfoArgs) {
            name = "serverInfo"
            description = "gives information about the server"
            action {
                val guild =
                    arguments.serverId.parsed?.let { this@UtilityExtension.kord.getGuild(it) } ?: this.guild?.asGuild()
                if (guild == null) {
                    respond { content = tr("serverInfo.guildOnlyOrArgumentPassed") }
                    return@action
                }
                val memberCount = guild.memberCount ?: 0
                val userCount = guild.members.filter { !it.isBot }.count()
                val botCount = guild.members.count() - userCount
                val iconUrl = guild.iconUrl()
                val bannerUrl = guild.bannerUrl()
                val splashUrl = guild.splashUrl()
                respond {
                    embed {
                        thumbnail {
                            url = iconUrl.toString()
                        }
                        description = tr(
                            "serverInfo.responseDescription", guild.name, guild.id,
                            guild.owner.asUser().tag,
                            guild.id.timestamp.toMessageFormat(DiscordTimestampStyle.ShortDateTime),
                            tr("serverVerificationLevel", guild.verificationLevel.value),
                            tr("serverMFATier", guild.mfaLevel.value),
                            tr("serverContentFilterLevel", guild.contentFilter.value),
                            tr("serverNSFWLevel", guild.nsfw.value),
                            true,
                            memberCount,
                            userCount, userCount.toFloat() / memberCount * 100,
                            botCount, botCount.toFloat() / memberCount * 100,
                            guild.premiumSubscriptionCount ?: 0,
                            tr("serverBoostTier", guild.premiumTier.value),
                            guild.roles.count(),
                            guild.channels.filter { it is TextChannel }.count(),
                            guild.channels.filter { it is VoiceChannel }.count(),
                            guild.channels.filter { it is Category }.count(),
                            iconUrl != null, iconUrl.toString(), bannerUrl != null, bannerUrl.toString(),
                            splashUrl != null, splashUrl.toString()
                        )
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


    private fun getStatusIcons(voiceState: VoiceState?): String {
        if (voiceState == null) {
            return ""
        }
        var list = ""
        if (voiceState.isSelfDeafened) list += " <:deafened:964134465884553256>"
        if (voiceState.isMuted) list += " <:server_muted:964134465880342578>"
        if (voiceState.isDeafened) list += " <:server_deafened:964134465884545094>"
        if (voiceState.isSelfMuted) list += " <:muted:964134465817440317>"
        if (voiceState.isSelfVideo) list += " <:video:964140322336698398>"
        if (voiceState.isSelfStreaming) list += " <:screen_share:964141257595158588>"
        return list
    }

    private fun FollowupMessageCreateBuilder.avatarEmbed(
        translationsProvider: TranslationsProvider,
        locale: Locale,
        target: User
    ) {
        embed {
            title = translationsProvider.tr("avatar.title", locale, target.tag)
            description = translationsProvider.tr(
                "avatar.description", locale, " **" +
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

    inner class ServerInfoArgs : Arguments() {

        val serverId = optionalSnowflake {
            name = "serverId"
            description = "Id of the server"
            validate {
                val betterValue = value ?: return@validate
                failIf(message = tr("arguments.guildId.noGuild")) {
                    kord.getGuild(betterValue) == null
                }
            }
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

    inner class UserInfoArgs : Arguments() {

        val user = optionalUser {
            name = "user"
            description = "A user"
        }
    }

    private fun getBadge(flag: UserFlag): String {
        return when (flag) {
            UserFlag.DiscordEmployee -> "<:furry:907322194156224542>"
            UserFlag.DiscordPartner -> "<:partnered:907322256567447552>"
            UserFlag.BugHunterLevel1 -> "<:bug_hunter:907322130151141416>"
            UserFlag.BugHunterLevel2 -> "<:gold_bughunter:907322205917052978>"
            UserFlag.HypeSquad -> "<:hypesquad_events_v1:907322220056023080>"
            UserFlag.HouseBravery -> "<:bravery:907322115454300190>"
            UserFlag.HouseBrilliance -> "<:brilliance:907322122580406332>"
            UserFlag.HouseBalance -> "<:balance:907321974211108984>"
            UserFlag.EarlySupporter -> "<:early_supporter:907322161159626753>"
            UserFlag.TeamUser -> "`team user`"
            UserFlag.VerifiedBot -> "`verified bot`"
            UserFlag.VerifiedBotDeveloper -> "<:early_verified_developer:907322174329716818>"
            UserFlag.DiscordCertifiedModerator -> "<:certified_virgin:907322144109756426>"
            UserFlag.BotHttpInteractions -> "`http bot`"
            UserFlag.System -> "`System User`"
        }
    }
}
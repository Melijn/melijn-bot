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
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageEdit
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.JDAUtil.asTag
import me.melijn.bot.utils.JDAUtil.toHex
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.format
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.utils.TimeFormat
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.koin.core.component.inject
import java.awt.Color
import java.util.*


@KordExtension
class UtilityExtension : Extension() {

    override val name: String = "utility"

    override suspend fun setup() {
        publicSlashCommand(::AvatarArgs) {
            name = "avatar"
            description = "Shows avatar"
            action {
                val target = (arguments.target.parsed ?: this.user)
                respond {
                    avatarEmbed(translationsProvider, resolvedLocale.await(), target)
                }
            }
        }
        publicUserCommand {
            name = "avatar"
            action {
                val target = this.target
                respond {
                    avatarEmbed(translationsProvider, resolvedLocale.await(), target)
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

            action {
                val user = arguments.user.parsed ?: this.user
                val guild = guild!!
                val member = guild.retrieveMember(user).await()
                val isSupporter = false
                val profile = user.retrieveProfile().await()
                val voiceState = member.voiceState
                val statusIconString = getStatusIcons(voiceState)
                respond {
                    embed {
                        description = tr("userInfo.userInfoSection", user.name, user.discriminator,
                            user.id, user.isBot, isSupporter, user.effectiveAvatarUrl,
                            profile.banner != null, profile.bannerUrl,
                            TimeFormat.DATE_TIME_SHORT.format(user),
                            user.flags.joinToString(separator = " ") { getBadge(it) })
                        val roleString = member.roles.toList()
                            .sortedBy { it.positionRaw }
                            .reversed()
                            .joinToString {
                                it.asMention
                            }
                        description += tr(
                            "userInfo.memberInfoSection", roleString,
                            member.nickname ?: "",
                            member.isOwner,
                            TimeFormat.DATE_TIME_SHORT.format(member.timeJoined),
                            member.timeBoosted?.let { TimeFormat.DATE_TIME_SHORT.format(it) } ?: "",
                            voiceState?.inAudioChannel(), statusIconString, guild.selfMember.canInteract(member)
                        )
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
                            guild.owner?.asTag,
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
    }


    private fun getStatusIcons(voiceState: GuildVoiceState?): String {
        if (voiceState == null) {
            return ""
        }
        var list = ""
        if (voiceState.isSelfDeafened) list += " <:deafened:964134465884553256>"
        if (voiceState.isMuted) list += " <:server_muted:964134465880342578>"
        if (voiceState.isDeafened) list += " <:server_deafened:964134465884545094>"
        if (voiceState.isSelfMuted) list += " <:muted:964134465817440317>"
        if (voiceState.isSendingVideo) list += " <:video:964140322336698398>"
        if (voiceState.isStream) list += " <:screen_share:964141257595158588>"
        return list
    }

    private fun InlineMessage<MessageCreateData>.avatarEmbed(
        translationsProvider: TranslationsProvider,
        locale: Locale,
        target: User
    ) {
        embed {
            title = translationsProvider.tr("avatar.title", locale, target.asTag)
            description = translationsProvider.tr(
                "avatar.description", locale, " **" +
                        "[direct](${target.effectiveAvatarUrl}) • " +
                        "[x64](${target.effectiveAvatarUrl}?size=64) • " +
                        "[x128](${target.effectiveAvatarUrl}?size=128) • " +
                        "[x256](${target.effectiveAvatarUrl}?size=256) • " +
                        "[x512](${target.effectiveAvatarUrl}?size=512) • " +
                        "[x1024](${target.effectiveAvatarUrl}?size=1024) • " +
                        "[x2048](${target.effectiveAvatarUrl}?size=2048)**"
            )
            image = target.effectiveAvatarUrl + "?size=2048"
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
            name = "serverid"
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
            User.UserFlag.STAFF -> "<:furry:907322194156224542>"
            User.UserFlag.PARTNER -> "<:partnered:907322256567447552>"
            User.UserFlag.BUG_HUNTER_LEVEL_1 -> "<:no_life:907322130151141416>"
            User.UserFlag.BUG_HUNTER_LEVEL_2 -> "<:no_life2:907322205917052978>"
            User.UserFlag.HYPESQUAD -> "<:hypesquad_events_v1:907322220056023080>"
            User.UserFlag.HYPESQUAD_BRAVERY -> "<:bravery:907322115454300190>"
            User.UserFlag.HYPESQUAD_BRILLIANCE -> "<:brilliance:907322122580406332>"
            User.UserFlag.HYPESQUAD_BALANCE -> "<:balance:907321974211108984>"
            User.UserFlag.EARLY_SUPPORTER -> "<:early_supporter:907322161159626753>"
            User.UserFlag.TEAM_USER -> "`team user`"
            User.UserFlag.VERIFIED_BOT -> "`verified bot`"
            User.UserFlag.VERIFIED_DEVELOPER -> "<:verified_code_slave:907322174329716818>"
            User.UserFlag.CERTIFIED_MODERATOR -> "<:certified_virgin:907322144109756426>"
            User.UserFlag.BOT_HTTP_INTERACTIONS -> "`http bot`"
            User.UserFlag.ACTIVE_DEVELOPER -> "<:code_slave:>"
            User.UserFlag.UNKNOWN -> "`\uD83E\uDE78`"
        }
    }
}
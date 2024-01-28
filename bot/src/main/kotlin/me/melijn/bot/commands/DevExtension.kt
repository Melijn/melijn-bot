package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.boolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sun.management.OperatingSystemMXBean
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.messages.InlineEmbed
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.JDAUtil.createMessage
import me.melijn.bot.utils.JvmUsage
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.guildChatCommand
import me.melijn.bot.utils.KordExUtils.respond
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.StringsUtil
import me.melijn.bot.utils.SystemUtil
import me.melijn.bot.utils.TimeUtil
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.utils.StringUtils
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.RichPresence
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.MessageChannelMixin
import org.koin.core.component.inject
import org.springframework.boot.ansi.AnsiColor
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.milliseconds

@KordExtension
class DevExtension : Extension() {

    override val name: String = "dev"

    override suspend fun setup() {
        chatCommand {
            name = "clearServerCommands"
            check {
                anyGuild()
                userIsOwner()
            }
            action {
                val guild = this.guild!!
                guild.updateCommands().await()
                respond {
                    content = "Cleared guild commands"
                }
            }
        }

        chatCommand {
            name = "stats"

            action {
                val bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
                val (totalMem, usedMem, totalJVMMem, usedJVMMem) = JvmUsage.current(bean)
                val blue = StringsUtil.ansiFormat(AnsiColor.BLUE)
                val reset = StringsUtil.ansiFormat(AnsiColor.DEFAULT)
                respond {
                    content = """```ANSI
                    |${blue}Memory usage${reset}: ${usedMem}/${totalMem} MB
                    |${blue}JVM Mem usage${reset}: $usedJVMMem/$totalJVMMem MB
                    |${blue}Threads${reset}: ${Thread.activeCount()}/${Thread.getAllStackTraces().size}
                    |${blue}CPU Load${reset}: ${DecimalFormat("###.###%").format(bean.processCpuLoad)}
                    |${blue}Cores${reset}: ${bean.availableProcessors}
                    |${blue}Uptime${SystemUtil.getUnixUptime().milliseconds.formatElapsed()}
                    |```""".trimMargin()
                }
            }
        }
        publicSlashCommand {
            name = "test"
            description = "test"

            action {
                respond {
                    content = "blub"
                }
            }
        }

        guildChatCommand(::PresenceArgs) {
            name = "presence"
            description = "dumps a user's presence"

            action {
                val target = arguments.user

                val member = this.guild!!.retrieveMembers(true, listOf(target)).await()
                    ?.firstOrNull() ?: bail("Couldn't fetch their presence")

                // get presence lines
                val possibleMusicActivities = member.activities
                if (arguments.raw) {
                    respond("```$possibleMusicActivities```")
                }

                val split = possibleMusicActivities.chunked(5)

                for (activities in split) {
                    respond {
                        for (activity in activities) {
                            embed {
                                title = activity.name
                                addActivityIntoEmbed(activity)
                            }
                        }
                    }
                }
            }
        }

        chatCommand(::DumpMessageArgs) {
            name = "dump"
            description = "dump message content and embed"
            action {
                val linkArg = arguments.messageLink.parsed
                val message = if (linkArg != null) {
                    val parts = linkArg.split("/").takeLast(3)
                    val guild = shardManager.getGuildById(parts[0])
                    val channel = guild?.getChannel<GuildMessageChannel>(parts[1])
                    if (channel == null) {
                        this.channel.createMessage("that link is veeery stinky")
                        return@action
                    }
                    channel.retrieveMessageById(parts[2]).await()
                } else {
                    message.referencedMessage!!
                }
                val blue = StringsUtil.ansiFormat(AnsiColor.BLUE)
                val reset = StringsUtil.ansiFormat(AnsiColor.DEFAULT)
                var content = ""
                content += "${blue}content: ${reset}${message.contentRaw.replace("```", "'''")}\n"
                content += "${blue}embed: $reset"
                if (message.embeds.isNotEmpty()) {
                    content += "[\n" + message.embeds.joinToString(",\n") {
                        it.toData().toPrettyString()
                            .replace("```", "'''")
                    } + "\n]"
                }
                content += "\n"
                content += "${blue}attachments: $reset"
                if (message.attachments.isNotEmpty()) {
                    content += "[\n" + message.attachments.joinToString("\n") { it.fileName + " - " + it.url } + "\n]"
                }
                content += "\n"
                paginator(targetChannel = channel) {
                    val parts = StringUtils.splitMessage(content)
                    for (part in parts) {
                        page {
                            title = "dumped ${message.id}"
                            description = "```ansi\n$part```"
                        }
                    }
                }.send()
            }
        }
    }

    private fun SlashCommandContext<*, *>.getNames(): List<String> {
        return buildList {
            add(event.interaction.name)
            event.interaction.subcommandGroup?.let { add(it) }
            event.interaction.subcommandName?.let { add(it) }
        }
    }

    companion object {
        fun activityNameMatch(name: String): (activity: Activity) -> Boolean = { it.name == name }

        context(InlineEmbed)
        fun addActivityIntoEmbed(activity: Activity) {
            val richPresence = activity.asRichPresence()
            when {
                maybeMusicAction(activity) -> {
                    val musicData = richPresence?.let { getMusicFields(it) } ?: bail("\uD83D\uDC12")
                    title = musicData.title
                    author {
                        name = musicData.author.joinToString(", ")
                    }
                }

                else -> {
                    url = activity.url
                    thumbnail = richPresence?.largeImage?.url
                    timestamp = activity.timestamps?.startTime
                    description = """$activity"""
                }
            }
        }

        fun getMusicFields(activity: RichPresence): PresenceMusicFields? {
            return when {
                PresenceMusicProviders.SPOTIFY.detectingFunction(activity) -> {
                    val songName = activity.details ?: return null
                    val songAuthors = activity.state?.split("; ") ?: emptyList()

                    PresenceMusicFields(songName, songAuthors, PresenceMusicProviders.SPOTIFY)
                }

                PresenceMusicProviders.YOUTUBE_MUSIC_OSS.detectingFunction(activity) -> {
                    val songName = activity.details ?: return null
                    val songAuthors = activity.state?.split("; ") ?: emptyList()

                    PresenceMusicFields(songName, songAuthors, PresenceMusicProviders.YOUTUBE_MUSIC_OSS)
                }

                else -> null
            }
        }

        private fun maybeMusicAction(activity: Activity): Boolean {
            val musicActionCheck = activity.name == "Spotify" || activity.name == "YouTube Music"
            return musicActionCheck && activity.asRichPresence()?.let { getMusicFields(it) } != null
        }
    }

    enum class TrackFetchMethod {
        SPOTIFY,
        YT
    }

    enum class PresenceMusicProviders(val fetchMethod: TrackFetchMethod, val detectingFunction: (Activity) -> Boolean) {
        YOUTUBE_MUSIC_OSS(TrackFetchMethod.YT, activityNameMatch("YouTube Music")),
        SPOTIFY(TrackFetchMethod.SPOTIFY, activityNameMatch("Spotify"));
    }

    data class PresenceMusicFields(
        val title: String,
        val author: List<String>,
        val detectedProvider: PresenceMusicProviders,
    )

    class PresenceArgs : Arguments() {
        val user by member {
            name = "member"
            description = "Member of which you wish to view their presence."
        }
        val raw by boolean {
            name = "raw"
            description = "Weather to show you the raw presence data"
        }
    }

    class DumpMessageArgs : Arguments() {
        val messageLink = optionalString {
            name = "messageLink"
            description = "discord formatted message link"

            validate {
                val a = value
                if (a != null) {
                    failIf("Not a normal message link!") { !".*/\\d+|@me/\\d+/\\d+".toRegex().matches(a) }
                } else {
                    val eventObj = context.eventObj
                    val ref = if (eventObj is MessageReceivedEvent) {
                        eventObj.message.referencedMessage
                    } else null
                    failIf("stinkert!") { ref?.jumpUrl == null }
                }
            }
        }

        override fun validate() {
            messageLink.parsed
        }
    }
}
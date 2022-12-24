package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.boolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.requestMembers
import dev.kord.core.cache.data.ActivityData
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.RootCommand
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.model.kordex.PersistentUsageLimitType
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.StringsUtil
import me.melijn.kordkommons.utils.StringUtils
import org.springframework.boot.ansi.AnsiColor
import kotlin.time.Duration.Companion.seconds

@KordExtension
class DevExtension : Extension() {

    override val name: String = "dev"

    @OptIn(PrivilegedIntent::class)
    override suspend fun setup() {
        publicSlashCommand {
            name = "test"
            description = "test"
            cooldown(PersistentUsageLimitType.USER_COMMAND) {
                50.seconds
            }
            action {
                respond {
                    val names = getNames()

                    content = "called `${names.joinToString(" ")}`"
                }
            }
        }
        publicSlashCommand {
            name = "testsub"
            description = "test"
            publicSubCommand {
                name= "test"
                description = "testing"
                action {
                    respond {
                        val names = getNames()

                        content = "called `${names.joinToString(" ")}`"
                    }
                }
            }
        }
        publicSlashCommand {
            name = "testsubsub"
            description = "test"
            group("sub1") {
                description = "sin1"

                publicSubCommand {
                    name= "test"
                    description = "testing"
                    action {
                        respond {
                            val names = getNames()

                            content = "called `${names.joinToString(" ")}`"
                        }
                    }
                }
            }
        }

        chatGroupCommand {
            name = "6"
            chatCommand {
                name = "5"
                action {
                    this.channel.createMessage("5")
                }
            }
        }

        chatCommand(::PresenceArgs) {
            name = "presence"
            description = "dumps a user's presence"

            action {
                val target = arguments.user

                val member = this@DevExtension.kord.getGuildOrNull(this.guild!!.id)?.requestMembers {
                    userIds.add(target.id)
                    presences = true
                }?.firstOrNull() ?: bail("Couldn't fetch their presence")

                // get presence lines
                val presences = member.data.presences.value
                if (arguments.raw) {
                    this.channel.createMessage {
                        content = "```${presences.toString()}```"
                    }
                }

                val possibleMusicActivities = presences?.map { presence ->
                    presence.activities
                } ?: emptyList()

                val split = possibleMusicActivities.flatten().chunked(5)

                for (activities in split) {
                    this.channel.createMessage {
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
                    val parts = linkArg.split("/").takeLast(3).map { it.toULong() }
                    val guild = this@DevExtension.kord.getGuildOrNull(Snowflake(parts[0]))
                    val channel = guild?.getChannelOrNull(Snowflake(parts[1]))
                    if (channel == null || channel !is MessageChannel) {
                        this.channel.createMessage("that link is veeery stinky")
                        return@action
                    }
                    channel.getMessage(Snowflake(parts[2]))
                } else {
                    message.referencedMessage!!
                }
                val blue = StringsUtil.ansiFormat(AnsiColor.BLUE)
                val reset = StringsUtil.ansiFormat(AnsiColor.DEFAULT)
                var content = ""
                content += "${blue}content: ${reset}${message.content.replace("```", "'''")}\n"
                content += "${blue}embed: $reset"
                if (message.embeds.isNotEmpty()) {
                    content += "[\n" + message.embeds.joinToString(",\n") {
                        Json.encodeToString(
                            it.data
                        ).replace("```", "'''")
                    } + "\n]"
                }
                content += "\n"
                content += "${blue}attachments: $reset"
                if (message.attachments.isNotEmpty()) {
                    content += "[\n" + message.attachments.joinToString("\n") { it.filename + " - " + it.url } + "\n]"
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
           when (val command = event.interaction.command) {
                is GroupCommand -> {
                    add(command.rootName)
                    add(command.groupName)
                    add(command.name)
                }
                is dev.kord.core.entity.interaction.SubCommand -> {
                    add(command.rootName)
                    add(command.name)
                }
                is RootCommand -> {
                    add(command.rootName)
                }
            }
        }
    }
    
    companion object {
        fun activityNameMatch(name: String): (activity: ActivityData) -> Boolean  = { it.name == name }

        context(EmbedBuilder)
        fun addActivityIntoEmbed(activity: ActivityData) {
            when {
                maybeMusicAction(activity) -> {
                    val musicData = getMusicFields(activity) ?: bail("\uD83D\uDC12")
                    title = musicData.title
                    author {
                        name = musicData.author.joinToString(", ")
                    }
                }

                else -> {
                    url = activity.url.value
                    thumbnail {
                        url = activity.assets.value?.largeImage?.value.toString()
                    }
                    timestamp = activity.timestamps.value?.start?.value
                    description = """$activity"""
                }
            }
        }

        fun getMusicFields(activity: ActivityData): PresenceMusicFields? {
            return when {
                PresenceMusicProviders.SPOTIFY.detectingFunction(activity) -> {
                    val songName = activity.details.value ?: return null
                    val songAuthors = activity.state.value?.split("; ") ?: emptyList()

                    PresenceMusicFields(songName, songAuthors, PresenceMusicProviders.SPOTIFY)
                }

                PresenceMusicProviders.YOUTUBE_MUSIC_OSS.detectingFunction(activity) -> {
                    val songName = activity.details.value ?: return null
                    val songAuthors = activity.state.value?.split("; ") ?: emptyList()

                    PresenceMusicFields(songName, songAuthors, PresenceMusicProviders.YOUTUBE_MUSIC_OSS)
                }

                else -> null
            }
        }

        private fun maybeMusicAction(activity: ActivityData): Boolean {
            val musicActionCheck = activity.name == "Spotify" || activity.name == "YouTube Music"
            return musicActionCheck && getMusicFields(activity) != null
        }
    }

    enum class TrackFetchMethod {
        SPOTIFY,
        YT
    }

    enum class PresenceMusicProviders(val fetchMethod: TrackFetchMethod, val detectingFunction: (ActivityData) -> Boolean) {
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
                    val ref = if (eventObj is MessageCreateEvent) {
                        eventObj.message.referencedMessage
                    } else null
                    failIf("stinkert!") { ref?.getJumpUrl() == null }
                }
            }
        }

        override fun validate() {
            messageLink.parsed
        }
    }
}

package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.editingPaginator
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ChannelType
import dev.kord.rest.builder.message.create.embed
import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.kord.connectAudio
import dev.schlaubi.lavakord.rest.TrackResponse
import dev.schlaubi.lavakord.rest.loadItem
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.Melijn
import me.melijn.bot.model.PartialUser
import me.melijn.bot.model.TrackSource
import me.melijn.bot.music.FetchedTrack
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.music.SkipType
import me.melijn.bot.music.TrackData
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.kordkommons.utils.StringUtils
import org.springframework.boot.ansi.AnsiColor
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KordExtension
class MusicExtension : Extension() {
    override val name: String = "music"

    override suspend fun setup() {
        publicSlashCommand {
            name = "queue"
            description = "shows queued tracks"

            check {
                anyGuild()
            }

            action {
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                val player = trackManager.player
                val playing = trackManager.playingTrack
                val queue = trackManager.queue
                if (playing == null && queue.isEmpty()) {
                    respond {
                        content = "The queue is empty"
                    }
                    return@action
                }


                var totalDuration = playing?.length ?: Duration.ZERO
                var description = ""

                description += if (playing == null) {
                    tr(
                        "queue.playingEntry", true, "", "nothing",
                        Duration.ZERO.formatElapsed(), Duration.ZERO.formatElapsed()
                    )
                } else {
                    tr(
                        "queue.playingEntry", player.paused, playing.url, playing.title,
                        player.positionDuration.formatElapsed(), playing.length.formatElapsed()
                    )
                }

                queue.indexedForEach { index, track ->
                    totalDuration += track.length
                    description += "\n" + tr(
                        "queue.queueEntry", index + 1, track.url, track.title,
                        track.length.formatElapsed()
                    )
                }

                description += tr("queue.fakefooter",
                    (totalDuration - player.positionDuration).formatElapsed(),
                    queue.size + (playing?.let { 1 } ?: 0)
                )


                editingPaginator {
                    val parts = StringUtils.splitMessage(description)
                    for (part in parts) {
                        page {
                            this.title = tr("queue.title")
                            this.description = part
                        }
                    }
                }.send()
            }
        }

        publicGuildSlashCommand(::SkipArgs) {
            name = "skip"
            description = "Skip tracks"

            check {
                val guild = guildFor(this.event)!!.asGuild()
                failIf("No songs to skip!") {
                    guild.getTrackManager().playingTrack == null
                }
            }

            action {
                val amount = arguments.number.parsed ?: 1
                val type = arguments.type.parsed ?: SkipType.HARD
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                val player = trackManager.player
                val skipped = trackManager.playingTrack ?: return@action

                val skippedPart = tr("skip.manyTracks", amount, skipped.url, skipped.title,
                    player.positionDuration.formatElapsed(), skipped.length.formatElapsed())

                trackManager.skip(amount, type)

                val next = trackManager.playingTrack
                val nextPart = if (next == null){
                    tr("skip.noNextTrack")
                } else {
                    tr("skip.nextTrack", next.url, next.title, next.length.formatElapsed())
                }
                respond {
                    embed {
                        title = tr("skip.title", user.asUser().tag)
                        description = """
                            $skippedPart
                            $nextPart
                        """.trimIndent()
                    }
                }
            }
        }

        publicSlashCommand {
            name = "nowplaying"
            description = "shows current playing song information"
            check {
                anyGuild()
            }

            action {
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                val player = trackManager.player
                val playing = trackManager.playingTrack
                if (playing == null) {
                    respond {
                        content = "There is no track playing"
                    }
                    return@action
                }

                val count = 30
                val progress = ((player.position * count.toDouble()) /
                    playing.length.inWholeMilliseconds.toDouble()).roundToInt()

                val ansiFormat = { color: AnsiColor -> "\u001B[0;${color}m" }
                val blue = ansiFormat(AnsiColor.BLUE)
                val green = ansiFormat(AnsiColor.GREEN)
                val reset = ansiFormat(AnsiColor.DEFAULT)

                val bar = "${blue}${"━".repeat(progress)}${green}${"━".repeat(count - progress)}${reset}"
                val status = if (player.paused) "paused" else "playing"


                respond {
                    embed {
                        title = tr("nowplaying.title")
                        description = tr("nowplaying.songLink", playing.title, playing.url)
                        field {
                            name = tr("nowplaying.progressFieldTitle")
                            value = tr(
                                "nowplaying.progressFieldValue", bar,
                                player.positionDuration.formatElapsed(), playing.length.formatElapsed()
                            )
                            inline = false
                        }
                        field {
                            name = tr("nowplaying.statusFieldTitle")
                            value = tr("nowplaying.statusFieldValue", status)
                            inline = false
                        }
                        thumbnail {
                            url = when (playing.sourceType) {
                                TrackSource.Youtube -> "https://img.youtube.com/vi/${playing.identifier}/hqdefault.jpg"
                                else -> ""
                            }
                        }
                    }
                }
            }
        }

        publicGuildSlashCommand(::VCArgs) {
            name = "connect"
            description = "bot joins your channel"

            action {
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                val channel = this.arguments.channel.parsed
                val link = trackManager.link
                if (channel == null && tryJoinUser(link)) return@action
                if (channel != null) {
                    link.connectAudio(channel.id)
                }

                respond {
                    val botVC = channel ?: member?.getVoiceState()?.getChannelOrNull()?.asChannel()
                    content = tr("connect.success", botVC?.mention ?: "error")
                }
            }
        }

        publicGuildSlashCommand(::PlayArgs) {
            name = "play"
            description = "bot joins your channel and plays moosic"

            action {
                val guild = guild!!.asGuild()
                val user = user.asUser()
                val trackManager = guild.getTrackManager()
                val link = trackManager.link

                val query = arguments.song.parsed
                val queuePosition = arguments.queuePosition.parsed ?: QueuePosition.BOTTOM
                val search = if (query.startsWith("http://") || query.startsWith("https://")) {
                    query
                } else {
                    "ytsearch:$query"
                }

                if (tryJoinUser(link)) return@action

                val item = link.loadItem(search)
                val oldQueueSize = trackManager.queue.size

                for (lavaKordTrack in item.tracks) {
                    val trackData = TrackData.fromNow(PartialUser.fromKordUser(user), lavaKordTrack.info.identifier)
                    val track = FetchedTrack.fromLavakordTrackWithData(lavaKordTrack.toTrack(), trackData)
                    trackManager.queue(track, queuePosition)
                }

                when (item.loadType) {
                    TrackResponse.LoadType.TRACK_LOADED, TrackResponse.LoadType.SEARCH_RESULT -> {
                        val lavaKordTrack = item.track
                        respond {
                            embed {
                                title = tr("play.title", user.tag)
                                description = tr(
                                    "play.description",
                                    lavaKordTrack.info.uri,
                                    lavaKordTrack.info.title,
                                    lavaKordTrack.info.length.toDuration(
                                        DurationUnit.MILLISECONDS
                                    ).formatElapsed()
                                )
                            }
                        }
                    }
                    TrackResponse.LoadType.PLAYLIST_LOADED -> {
                        respond {
                            embed {
                                title = tr("play.manyAddedTitle", user.tag)
                                description = tr(
                                    "play.manyAddedDescription",
                                    item.tracks.size,
                                    oldQueueSize,
                                    trackManager.queue.size
                                )
                            }
                        }
                    }
                    TrackResponse.LoadType.NO_MATCHES -> respond { content = "no matches" }
                    TrackResponse.LoadType.LOAD_FAILED -> respond { content = "Error: ${item.exception?.message}" }
                    else -> {}
                }


            }
        }

        publicGuildSlashCommand {
            name = "stop"
            description = "stops music"

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                link.player.stopTrack()
                respond {
                    content = "stopped"
                }
            }
        }

        publicGuildSlashCommand {
            name = "leave"
            description = "leaves channel"

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                link.destroy()

                respond {
                    content = "left"
                }
            }
        }

        publicGuildSlashCommand {
            name = "pause"
            description = "pause music"

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                link.player.pause(!link.player.paused)
                respond { content = "paused = ${!link.player.paused}" }
            }
        }
    }

    /**
     * Bot tries to join the user's vc if it isn't in a voiceChannel, if user is also not in vc, respond with error
     * @return true for failure
     */
    private suspend fun PublicSlashCommandContext<*>.tryJoinUser(link: Link): Boolean {
        if (link.state != Link.State.CONNECTED) {
            val vc = member?.getVoiceStateOrNull()?.channelId
            if (vc == null) {
                respond {
                    content = tr("music.userNotInVC")
                }
                return true
            }
            link.connectAudio(vc.value)
        }
        return false
    }

    private class PlayArgs : Arguments() {
        val song = string {
            name = "song"
            description = "songName"
        }
        val queuePosition = optionalEnumChoice<QueuePosition> {
            name = "queuePosition"
            description = "Position the queued track will take"
            typeName = "queuePosition"
        }
    }

    inner class SkipArgs : Arguments() {
        val number = optionalInt {
            name = "trackAmount"
            description =
                "Amount of track you want to skip, equal to the track index of the to play next track after skipping"

            validate {
                atLeast(name, 1)
            }
        }
        val type = optionalEnumChoice<SkipType> {
            name = "skipType"
            description = "Hard won't requeue when looping, Soft will"
            typeName = "skipType"
        }
    }

    private class VCArgs : Arguments() {
        val channel = optionalChannel {
            name = "voiceChannel"
            description = "Used for advanced summoning spells"

            validate {
                val channel = this.value
                failIf("This is not a voiceChannel!") {
                    channel != null
                        && channel.type != ChannelType.GuildVoice
                        && channel.type != ChannelType.GuildStageVoice
                }
            }
        }
    }
}
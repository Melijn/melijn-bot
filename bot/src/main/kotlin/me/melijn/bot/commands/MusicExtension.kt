package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.editingPaginator
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.User
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.kord.connectAudio
import dev.schlaubi.lavakord.rest.TrackResponse
import dev.schlaubi.lavakord.rest.loadItem
import kotlinx.coroutines.flow.firstOrNull
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.Melijn
import me.melijn.bot.model.PartialUser
import me.melijn.bot.model.TrackSource
import me.melijn.bot.music.*
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.bot.web.api.MySpotifyApi
import me.melijn.bot.web.api.MySpotifyApi.Companion.toTrack
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.utils.StringUtils
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import org.springframework.boot.ansi.AnsiColor
import kotlin.math.roundToInt
import kotlin.time.Duration

@KordExtension
class MusicExtension : Extension() {

    override val name: String = "music"
    val webManager by inject<WebManager>()

    inner class FollowUserArgs : Arguments() {
        val target = optionalUser {
            name = "target"
            description = "musicplayer will follow spotify status"
        }
    }

    override suspend fun setup() {
        publicGuildSlashCommand(::FollowUserArgs) {
            name = "followUser"
            description = "MusicPlayer will follow user's spotify status, skip to fetch again"

            check {
                userIsOwner()
            }

            action {
                val target = arguments.target.parsed
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                if (tryJoinUser(trackManager.link)) return@action
                trackManager.follow(target)
                respond {
                    content = "following ${target?.mention ?: "no one"}"
                }

                if (target == null) return@action
                webManager.spotifyApi?.let { trackManager.playFromTarget(it, target) }
            }
        }

        publicGuildSlashCommand {
            name = "queue"
            description = "shows queued tracks"

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

                val skippedPart = tr(
                    "skip.manyTracks", amount, skipped.url, skipped.title,
                    player.positionDuration.formatElapsed(), skipped.length.formatElapsed()
                )

                trackManager.skip(amount, type)

                val next = trackManager.playingTrack
                val nextPart = if (next == null) {
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

        publicGuildSlashCommand {
            name = "nowplaying"
            description = "shows current playing song information"

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

                if (tryJoinUser(link)) return@action

                val spotifyApi = webManager.spotifyApi
                val isHttpQuery = query.startsWith("http://") || query.startsWith("https://")
                val requester = PartialUser.fromKordUser(user)
                val tracks = if (isHttpQuery && (query.contains("open.spotify.com") && spotifyApi != null)) {
                    spotifyApi.getTracksFromSpotifyUrl(query, requester)
                } else {
                    val search = if (isHttpQuery) query else "ytsearch:$query"
                    val item = link.loadItem(search)

                    (when (item.loadType) {
                        TrackResponse.LoadType.TRACK_LOADED -> listOf(item.tracks.first())
                        TrackResponse.LoadType.PLAYLIST_LOADED -> item.tracks
                        TrackResponse.LoadType.SEARCH_RESULT -> listOf(item.tracks.first())
                        else -> emptyList()
                    }).map {
                        val lavakordTrack = it.toTrack()
                        FetchedTrack.fromLavakordTrackWithData(
                            lavakordTrack,
                            TrackData.fromNow(requester, it.info.identifier)
                        )
                    }
                }
                val oldQueueSize = trackManager.queue.size

                for (track in tracks) {
                    trackManager.queue(track, queuePosition)
                }

                when {
                    tracks.size == 1 -> {
                        val track = tracks.first()
                        respond {
                            embed {
                                title = tr("play.title", user.tag)
                                description = tr(
                                    "play.description",
                                    track.url,
                                    track.title,
                                    track.length.formatElapsed()
                                )
                            }
                        }
                    }
                    tracks.size > 1 -> {
                        respond {
                            embed {
                                title = tr("play.manyAddedTitle", user.tag)
                                description = tr(
                                    "play.manyAddedDescription",
                                    tracks.size,
                                    oldQueueSize,
                                    trackManager.queue.size
                                )
                            }
                        }
                    }
                    else -> respond { content = "No matches!" }
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

@OptIn(PrivilegedIntent::class)
suspend fun TrackManager.playFromTarget(
    spotifyApi: MySpotifyApi,
    parsed: User
) {

    val kord by inject<Kord>(Kord::class.java)

    /**
     * fetch full discord member which can have spotify presences since
     * we don't cache or store user presences
     * **/
    val targetMember = kord.getGuild(Snowflake(link.guildId))?.requestMembers {
        userIds.add(parsed.id)
        presences = true
    }?.firstOrNull()

    /** get spotify presences **/
    val presences = targetMember?.data?.presences?.value
    val possibleSpotifyActivities = presences?.mapNotNull { presence ->
        presence.activities.firstOrNull { it.name == "Spotify" }
    } ?: emptyList()
    val spotifyActivity = possibleSpotifyActivities.firstOrNull() ?: return
    val songName = spotifyActivity.details.value
    val songAuthors = spotifyActivity.state.value?.split("; ") ?: emptyList()

    val searchTerm = buildList {
        songName?.let { add(it) }
        songAuthors.firstOrNull()?.let { add(it) }
    }.joinToString(" ")

    val track = spotifyApi.searchTrack(searchTerm) ?: return
    play(track.toTrack(PartialUser.fromKordUser(parsed)))
}
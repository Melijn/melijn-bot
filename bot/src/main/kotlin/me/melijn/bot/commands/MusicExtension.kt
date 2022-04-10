package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.builders.ValidationContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.editingPaginator
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.suggestStringMap
import dev.kord.common.entity.ChannelType
import dev.kord.rest.builder.message.create.embed
import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.kord.connectAudio
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.Melijn
import me.melijn.bot.database.manager.PlaylistManager
import me.melijn.bot.database.manager.PlaylistTrackManager
import me.melijn.bot.model.PartialUser
import me.melijn.bot.model.TrackSource
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.music.SkipType
import me.melijn.bot.music.Track
import me.melijn.bot.music.TrackLoader
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.bot.utils.intRanges
import me.melijn.bot.utils.shortTime
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.utils.StringUtils
import me.melijn.kordkommons.utils.escapeMarkdown
import org.koin.core.component.inject
import org.springframework.boot.ansi.AnsiColor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration

@KordExtension
class MusicExtension : Extension() {

    override val name: String = "music"
    private val webManager by inject<WebManager>()
    private val trackLoader by inject<TrackLoader>()

    inner class FollowUserArgs : Arguments() {
        val target = optionalUser {
            name = "target"
            description = "MusicPlayer will follow spotify status"
        }
    }

    inner class SeekArgs : Arguments() {
        val time = shortTime {
            name = "timeStamp"
            description = "format mm:ss or hh:mm:ss (e.g. 1:35 for 1 minute 35 seconds)"
        }
    }

    inner class MoveArgs : Arguments() {
        val from = int {
            name = "from"
            description = "Index of the track you want to move (see queue command for viewing indexes)"
            validate {
                failIfInvalidTrackIndex()
            }
        }
        val to = int {
            name = "to"
            description = "Index where the track needs to be moved to"
            validate {
                failIfInvalidTrackIndex()
            }
        }


    }

    inner class RemoveArgs : Arguments() {
        val positions = intRanges {
            name = "positions"
            description = "Number ranges or numbers seperated by commas (e.g. 1,5,9-15)"
        }
    }

    inner class PlaylistAddArgs : Arguments() {
        val playlist = string {
            name = "playlist"
            description = "New or existing playlist name or existing playlist index"
            autoComplete {
                this.suggestStringMap(
                    mapOf(
                        "tok tok" to "chicken"
                    )
                )
            }
        }
        val trackIndex = optionalInt {
            name = "trackindex"
            description = "Index of a track in queue, see /queue"
            validate {
                failIfInvalidTrackIndex()
            }
        }
    }

    inner class PlaylistListArgs : Arguments() {
        val playlist = string {
            name = "playlist"
            description = "Existing playlist name"

            autoComplete {
                val playlists = playlistManager.getByIndex0(user.id.value)
                suggestStringMap(playlists.associate { it.name to it.name })
            }
            validate {
                val playlists = context.getUser()?.id?.value?.let { playlistManager.getByIndex0(it) } ?: return@validate
                val trimmed = value.trim()
                failIf(context.tr("playlist.playlistArgDoesNotExist", value)) {
                    playlists.none { it.name == trimmed }
                }
            }
        }
    }

    private suspend fun ValidationContext<Int?>.failIfInvalidTrackIndex() {
        val noVarMoment = value ?: return
        failIf(context.tr("move.invalidIndex", noVarMoment)) {
            val guild = context.getGuild()!!.asGuild()
            val trackManager = guild.getTrackManager()
            noVarMoment > trackManager.queue.size || noVarMoment < 1
        }
    }

    val playlistManager by inject<PlaylistManager>()
    val playlistTrackManager by inject<PlaylistTrackManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "playlist"
            description = "Manage playlist things"

            group("add") {
                description = "balls"
                publicSubCommand(::PlaylistAddArgs) {
                    name = "add"
                    description = "Adds a track to your playlist"

                    action {
                        val guild = guild!!.asGuild()
                        val trackManager = guild.getTrackManager()
                        val trackIndex = arguments.trackIndex.parsed

                        val targetTrack =
                            if (trackIndex != null) trackManager.getTrackByIndex(trackIndex)?.getLavakordTrack()
                            else trackManager.player.playingTrack
                        if (targetTrack == null) {
                            respond { content = ":?" }
                            return@action
                        }

                        val playlistName = arguments.playlist.parsed
                        val existingPlaylist = playlistManager.getByNameOrDefault(user.id, playlistName)
                        playlistManager.store(existingPlaylist)

                        playlistTrackManager.newTrack(existingPlaylist, targetTrack.track)
                        val tracks = playlistTrackManager.getTracksInPlaylist(existingPlaylist)

                        respond {
                            content = tr(
                                "playlist.add.added", targetTrack.title.escapeMarkdown(),
                                playlistName.escapeMarkdown(), tracks.size - 1
                            )
                        }
                    }
                }
            }

            group("remove") {
                description = "balls"
                publicSubCommand {
                    name = "single"
                    description = "Removes a track from your playlist"

                    action {

                    }
                }
            }

            group("list") {
                description = "balls"
                publicSubCommand() {
                    name = "all"
                    description = "Lists all your playlists"

                    action {
                        val existingPlaylist = playlistManager.getPlaylistsOfUserWithTrackCount(user.id)

                        var description = "```INI\n# index - public - [name] - tracks - [created]"

                        existingPlaylist.entries.withIndex().forEach { (index, playlistWithCount) ->
                            val (playlist, count) = playlistWithCount
                            description += "\n" + tr(
                                "playlist.list.all.playlistEntry", index, playlist.public, playlist.name, count,
                                playlist.created,
                            )
                        }
                        description += "```"

                        editingPaginator {
                            val parts = StringUtils.splitMessage(description)
                            for (part in parts) {
                                page {
                                    this.title = tr("playlist.list.all.listTitle", user.asUser().tag)
                                    this.description = part
                                }
                            }
                        }.send()
                    }
                }

                publicSubCommand(::PlaylistListArgs) {
                    name = "tracks"
                    description = "Lists your playlist tracks"

                    action {
                        val playlistName = arguments.playlist.parsed
                        val existingPlaylist = playlistManager.getByNameOrDefault(user.id, playlistName)
                        val tracks = playlistTrackManager.getTracksInPlaylist(existingPlaylist)
                        var totalDuration = Duration.ZERO
                        var description = ""

                        tracks.withIndex().forEach { (index, trackData) ->
                            val lavakordTrack = dev.schlaubi.lavakord.audio.player.Track.fromLavalink(trackData.track)
                            totalDuration += lavakordTrack.length
                            description += "\n" + tr(
                                "queue.queueEntry", index + 1, lavakordTrack.uri ?: "", lavakordTrack.title,
                                lavakordTrack.length.formatElapsed()
                            )
                        }

                        description += tr(
                            "playlist.list.fakeFooter",
                            totalDuration.formatElapsed(),
                            tracks.size
                        )

                        editingPaginator {
                            val parts = StringUtils.splitMessage(description)
                            for (part in parts) {
                                page {
                                    this.title = tr("playlist.list.listTitle", user.asUser().tag)
                                    this.description = part
                                }
                            }
                        }.send()
                    }
                }
            }
        }

        publicGuildSlashCommand {
            name = "shuffle"
            description = "shuffles the queue once"

            action {
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                trackManager.shuffle()
                respond {
                    content = tr("shuffle.shuffled")
                }
            }
        }

        publicGuildSlashCommand {
            name = "loop"
            description = "Loop commands"

            publicSubCommand {
                name = "queue"
                description = "Loops the queue"
                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()
                    trackManager.loopedQueue = !trackManager.loopedQueue
                    respond {
                        content = tr("loop.queue.looped", trackManager.loopedQueue)
                    }
                }
            }
            publicSubCommand {
                name = "single"
                description = "Loops the playing track"
                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()
                    trackManager.looped = !trackManager.looped
                    respond {
                        content = tr("loop.single.looped", trackManager.looped)
                    }
                }
            }
        }

        publicGuildSlashCommand(::RemoveArgs) {
            name = "remove"
            description = "Removes a track from the queue"

            action {
                val from = arguments.positions.parsed
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                val toRemove = mutableListOf<Track>()
                for (range in from.list)
                    for (i in range) {
                        val element = trackManager.queue.get(i - 1)
                        if (!toRemove.contains(element))
                            toRemove.add(element)
                    }

                trackManager.queue.removeAll(toRemove)
                respond {
                    content = "Removed ${toRemove.size} tracks"
                }
            }
        }

        publicGuildSlashCommand(::MoveArgs) {
            name = "move"
            description = "Moves a track to another position in the queue"

            action {
                val from = arguments.from.parsed - 1
                val to = arguments.to.parsed - 1
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()

                val trackList = trackManager.queue
                val track = trackList.removeAt(from)
                trackList.add(to, track)

                respond {
                    content = tr("move.moved", track.title.escapeMarkdown(), from + 1, to + 1)
                }
            }
        }

        publicGuildSlashCommand {
            name = "seek"
            description = "Seek to another timestamp in the track"

            publicSubCommand(::SeekArgs) {
                name = "position"
                description = "Seek to a position in the track"

                action {
                    val guild = guild!!.asGuild()
                    val position = arguments.time.parsed
                    val trackManager = guild.getTrackManager()
                    trackManager.seek(position)

                    respond {
                        content = tr(
                            "seek.seeked",
                            java.time.Duration.ofMillis(position).formatElapsed(),
                            trackManager.playingTrack?.length?.formatElapsed().toString()
                        )
                    }
                }
            }
            publicSubCommand(::SeekArgs) {
                name = "forward"
                description = "Forward to a position in the track"

                action {
                    val guild = guild!!.asGuild()
                    val position = arguments.time.parsed
                    val trackManager = guild.getTrackManager()
                    val newPos = trackManager.player.position + position
                    trackManager.seek(newPos)

                    respond {
                        content = tr(
                            "seek.forwarded",
                            java.time.Duration.ofMillis(newPos).formatElapsed(),
                            trackManager.playingTrack?.length?.formatElapsed().toString()
                        )
                    }
                }
            }
            publicSubCommand(::SeekArgs) {
                name = "rewind"
                description = "Rewind to a position in the track"

                action {
                    val guild = guild!!.asGuild()
                    val position = arguments.time.parsed
                    val trackManager = guild.getTrackManager()
                    val newPos = trackManager.player.position - position
                    trackManager.seek(newPos)

                    respond {
                        content = tr(
                            "seek.rewinded",
                            java.time.Duration.ofMillis(newPos).formatElapsed(),
                            trackManager.playingTrack?.length?.formatElapsed().toString()
                        )
                    }
                }
            }

        }

        publicGuildSlashCommand {
            name = "clearQueue"
            description = "Clears the queue"

            action {
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                trackManager.clear()
                respond {
                    content = tr("clearQueue.cleared")
                }
            }
        }

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
                val progress = (max(0.0, (player.position * count.toDouble())) /
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
            name = "summon"
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
                val oldQueueSize = trackManager.queue.size
                val link = trackManager.link

                val query = arguments.song.parsed
                val queuePosition = arguments.queuePosition.parsed ?: QueuePosition.BOTTOM

                if (tryJoinUser(link)) return@action

                val requester = PartialUser.fromKordUser(user)
                val tracks = trackLoader.searchTracks(link.node, query, requester)


                for (track in tracks) {
                    trackManager.queue(track, queuePosition)
                }

                respond {
                    when {
                        tracks.size == 1 -> {
                            val track = tracks.first()

                            embed {
                                title = tr("play.title", user.tag)
                                description = tr(
                                    "play.addedOne",
                                    trackManager.queue.size,
                                    track.url,
                                    track.title,
                                    track.length.formatElapsed()
                                )
                            }
                        }
                        tracks.size > 1 -> {
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
                        else -> content = tr("play.noMatches")
                    }
                }
            }
        }

        publicGuildSlashCommand {
            name = "stop"
            description = "stops music"

            action {
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                trackManager.stopAndDestroy()

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
                val guild = guild!!.asGuild()
                val trackManager = guild.getTrackManager()
                trackManager.player.pause(!trackManager.player.paused)
                respond {
                    content = tr("pause.paused", trackManager.player.paused)
                }
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
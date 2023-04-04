package me.melijn.bot.commands.music

import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.builders.ValidationContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.editingPaginator
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.interactions.components.secondary
import dev.schlaubi.lavakord.audio.Link
import kotlinx.coroutines.delay
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.Melijn
import me.melijn.bot.cache.SearchPlayMenuCache
import me.melijn.bot.events.buttons.SPlayMenuButtonHandler.Companion.SPLAY_BTN_CANCEL
import me.melijn.bot.events.buttons.SPlayMenuButtonHandler.Companion.SPLAY_BTN_ID_PREFIX
import me.melijn.bot.model.OwnedGuildMessage
import me.melijn.bot.model.PartialUser
import me.melijn.bot.model.SearchPlayMenu
import me.melijn.bot.model.TrackSource
import me.melijn.bot.music.*
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSubCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.KordExUtils.userIsOwner
import me.melijn.bot.utils.StringsUtil.ansiFormat
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.bot.utils.TimeUtil.formatRelative
import me.melijn.bot.utils.intRanges
import me.melijn.bot.utils.shortTime
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.utils.StringUtils
import me.melijn.kordkommons.utils.escapeMarkdown
import net.dv8tion.jda.api.entities.channel.ChannelType
import org.koin.core.component.inject
import org.springframework.boot.ansi.AnsiColor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KordExtension
class MusicExtension : Extension() {

    override val name: String = "music"
    private val webManager by inject<WebManager>()
    private val trackLoader by inject<TrackLoader>()

    companion object {

        suspend fun ValidationContext<*>.failIfInvalidTrackIndex(
            index: Int?,
            trackManagerFun: suspend ValidationContext<*>.() -> TrackManager = {
                context.guild!!.getTrackManager()
            }
        ) {
            val noVarMoment = index ?: return
            failIf(context.tr("move.invalidIndex", noVarMoment)) {
                val trackManager = trackManagerFun()
                index > trackManager.queue.size || index < 1
            }
        }

        /**
         * Bot tries to join the user's vc if it isn't in a voiceChannel, if user is also not in vc, respond with error
         * @return true for failure
         */
        suspend fun PublicSlashCommandContext<*>.tryJoinUser(link: Link): Boolean {
            if (link.state != Link.State.CONNECTED) {
                val vc = member?.voiceState?.channel?.idLong
                if (vc == null) {
                    respond {
                        content = tr("music.userNotInVC")
                    }
                    return true
                }
                link.connectAudio(vc.toULong())
            }
            return false
        }
    }

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "shuffle"
            description = "shuffles the queue once"

            action {
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                trackManager.shuffle()
                respond {
                    content = tr("shuffle.shuffled")
                }
            }
        }

        publicGuildSlashCommand {
            name = "fix"
            description = "try to fix broken player"

            action {
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                if (!trackManager.link.node.available) {
                    respond {
                        content = "Your audio node is currently not available, try again later."
                    }
                    return@action
                }

                val playingTrack = trackManager.playingTrack ?: return@action
                trackManager.link.disconnectAudio()

                delay(1000)
                if (tryJoinUser(trackManager.link)) return@action
                delay(1000)
                trackManager.play(playingTrack)

                respond {
                    content = tr("fix.fixed")
                }
            }
        }

        publicGuildSlashCommand {
            name = "loop"
            description = "Loop commands"

            publicGuildSubCommand {
                name = "queue"
                description = "Loops the queue"
                action {
                    val guild = guild!!
                    val trackManager = guild.getTrackManager()
                    trackManager.loopedQueue = !trackManager.loopedQueue
                    respond {
                        content = tr("loop.queue.looped", trackManager.loopedQueue)
                    }
                }
            }
            publicGuildSubCommand {
                name = "single"
                description = "Loops the playing track"
                action {
                    val guild = guild!!
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
                val guild = guild!!
                val trackManager = guild.getTrackManager()

                // collect tracks from queue to remove from intRanges
                val toRemove = mutableListOf<Track>()
                for (range in from.list)
                    for (i in range) {
                        val element = trackManager.queue.get(i - 1) // queue is offset by one
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
                val guild = guild!!
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

            publicGuildSubCommand(::SeekArgs) {
                name = "position"
                description = "Seek to a position in the track"

                action {
                    val guild = guild!!
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
            publicGuildSubCommand(::SeekArgs) {
                name = "forward"
                description = "Forward to a position in the track"

                action {
                    val guild = guild!!
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
            publicGuildSubCommand(::SeekArgs) {
                name = "rewind"
                description = "Rewind to a position in the track"

                action {
                    val guild = guild!!
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
            name = "clearqueue"
            description = "Clears the queue"

            action {
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                trackManager.clear()
                respond {
                    content = tr("clearQueue.cleared")
                }
            }
        }

        publicGuildSlashCommand(::FollowUserArgs) {
            name = "followuser"
            description = "MusicPlayer will follow user's spotify status, skip to fetch again"

            check {
                userIsOwner()
            }

            action {
                val target = arguments.target
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                if (tryJoinUser(trackManager.link)) return@action
                trackManager.follow(target)
                respond {
                    content = "The music player is now following ${target?.asMention ?: "no one"}"
                }

                if (target == null) return@action
                val track = trackLoader.fetchTrackFromPresence(guild, target.user) ?: return@action
                trackManager.play(track)
            }
        }

        publicGuildSlashCommand {
            name = "queue"
            description = "shows queued tracks"

            action {
                val guild = guild!!
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

                val locale = resolvedLocale.await()
                queue.indexedForEach { index, track ->
                    totalDuration += track.length
                    description += "\n" + translationsProvider.tr(
                        "queue.queueEntry", locale, index + 1, track.url, track.title,
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
                val guild = guildFor(this.event)!!
                failIf("No songs to skip!") {
                    guild.getTrackManager().playingTrack == null
                }
            }

            action {
                val amount = arguments.number.parsed ?: 1
                val type = arguments.type.parsed ?: SkipType.HARD
                val guild = guild!!
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
                        title = tr("skip.title", user.asTag)
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
                val guild = guild!!
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

                val blue = ansiFormat(AnsiColor.BLUE)
                val green = ansiFormat(AnsiColor.GREEN)
                val reset = ansiFormat(AnsiColor.DEFAULT)

                val bar = "${blue}${"━".repeat(progress)}${green}${"━".repeat(max(0, count - progress))}${reset}"
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

                             thumbnail = when (playing.sourceType) {
                                TrackSource.YOUTUBE -> "https://img.youtube.com/vi/${playing.identifier}/hqdefault.jpg"
                                else -> ""
                            }

                    }
                }
            }
        }

        publicGuildSlashCommand(MusicExtension::VCArgs) {
            name = "summon"
            description = "bot joins your channel"

            action {
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                val channel = this.arguments.channel.parsed
                val link = trackManager.link
                if (channel == null && tryJoinUser(link)) return@action
                if (channel != null) {
                    link.connectAudio(channel.id.toULong())
                }

                respond {
                    val botVC = channel ?: member?.voiceState?.channel
                    content = tr("connect.success", botVC?.asMention ?: "error")
                }
            }
        }

        val searchPlayMenuCache by inject<SearchPlayMenuCache>()
        publicGuildSlashCommand(::SearchPlayArgs) {
            name = "searchplay"
            description = "Search the first 5 tracks and lets you choose the best one"

            action {
                val guild = guild!!
                val user = user
                val trackManager = guild.getTrackManager()
                val link = trackManager.link

                val query = arguments.song.parsed
                val queuePosition = arguments.queuePosition.parsed ?: QueuePosition.BOTTOM

                if (tryJoinUser(link)) return@action

                val requester = PartialUser.fromKordUser(user)
                val tracks = trackLoader.searchFetchedTracks(link.node, query, requester, trackSearchKeep = 5)
                val locale = resolvedLocale.await()
                val entries = tracks.withIndex().joinToString("\n") { (index, track) ->
                    translationsProvider.tr(
                        "splay.entry",
                        locale,
                        index,
                        track.url,
                        track.title,
                        track.length.formatElapsed()
                    )
                }

                val msg = respond {
                    when {
                        tracks.size == 1 -> {
                            val track = tracks.first()
                            trackManager.queue(track, queuePosition)

                            embed {
                                title = tr("play.title", user.asTag)
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
                                title = tr("splay.menuTitle", user.asTag)
                                description = entries
                            }
                            actionRow(
                                tracks.indices.map {  i ->
                                    secondary("${SPLAY_BTN_ID_PREFIX}$i", "$i")
                                }
                            )
                            actionRow(danger(SPLAY_BTN_ID_PREFIX + SPLAY_BTN_CANCEL, tr("cancelButton")))
                        }
                        else -> content = tr("play.noMatches")
                    }
                }

                searchPlayMenuCache.cache[OwnedGuildMessage.from(guild, user, msg.referencedMessage!!)] = SearchPlayMenu(
                    tracks.toTypedArray(),
                    queuePosition
                )
            }
        }


        publicGuildSlashCommand(::PlayArgs) {
            name = "play"
            description = "bot joins your channel and plays moosic"

            action {
                val guild = guild!!
                val user = user
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
                                title = tr("play.title", user.asTag)
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
                                title = tr("play.manyAddedTitle", user.asTag)
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
                val guild = guild!!
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
                val guildId = guild!!.idLong
                val link = Melijn.lavalink.getLink(guildId.toULong())
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
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                trackManager.player.pause(!trackManager.player.paused)
                respond {
                    content = tr("pause.paused", trackManager.player.paused)
                }
            }
        }

        publicGuildSlashCommand {
            name = "musicplayer"
            description = "Shows detailed information about the current node"

            action {
                val guild = guild!!
                val trackManager = guild.getTrackManager()
                val stats = trackManager.link.node.lastStatsEvent
                respond {
                    content = tr(
                        "musicPlayer.playerInfo",
                        trackManager.player.playingTrack?.title ?: "",
                        trackManager.queue.size,
                        trackManager.looped, trackManager.loopedQueue, trackManager.player.paused
                    )

                    if (stats != null)
                        content += tr(
                            "musicPlayer.nodeStats",
                            trackManager.link.node.available,
                            stats.playingPlayers, stats.players,
                            stats.cpu.cores, stats.cpu.lavalinkLoad,
                            StringUtils.humanReadableByteCountBin(stats.memory.used, resolvedLocale.await()),
                            StringUtils.humanReadableByteCountBin(stats.memory.allocated, resolvedLocale.await()),
                            stats.frameStats?.deficit ?: 0, stats.frameStats?.nulled ?: 0, stats.frameStats?.sent ?: 0,
                            stats.uptime.toDuration(DurationUnit.MILLISECONDS).formatRelative()
                        )
                }
            }
        }
    }


    inner class FollowUserArgs : Arguments() {

        val target by optionalMember {
            name = "target"
            description = "MusicPlayer will follow spotify status"
        }
    }

    inner class SeekArgs : Arguments() {

        val time = shortTime {
            name = "timestamp"
            description = "format mm:ss or hh:mm:ss (e.g. 1:35 for 1 minute 35 seconds)"
        }
    }

    inner class MoveArgs : Arguments() {

        val from = int {
            name = "from"
            description = "Index of the track you want to move (see queue command for viewing indexes)"
            validate {
                failIfInvalidTrackIndex(value)
            }
        }
        val to = int {
            name = "to"
            description = "Index where the track needs to be moved to"
            validate {
                failIfInvalidTrackIndex(value)
            }
        }
    }

    inner class RemoveArgs : Arguments() {

        val positions = intRanges {
            name = "positions"
            description = "Number ranges or numbers seperated by commas (e.g. 1,5,9-15)"
        }
    }

    inner class PlayArgs : Arguments() {

        val song = string {
            name = "song"
            description = "songName or link"
        }
        val queuePosition = optionalEnumChoice<QueuePosition> {
            name = "queueposition"
            description = "Position the queued track will take"
            typeName = "queueposition"
        }
    }

    inner class SearchPlayArgs : Arguments() {

        val song = string {
            name = "song"
            description = "songName"
        }
        val queuePosition = optionalEnumChoice<QueuePosition> {
            name = "queueposition"
            description = "Position the queued track will take"
            typeName = "queueposition"
        }
    }

    inner class SkipArgs : Arguments() {

        val number = optionalInt {
            name = "trackamount"
            description =
                "Amount of track you want to skip, equal to the track index of the to play next track after skipping"

            validate {
                atLeast(name, 1)
            }
        }
        val type = optionalEnumChoice<SkipType> {
            name = "skiptype"
            description = "Hard won't requeue when looping, Soft will"
            typeName = "skiptype"
        }
    }

    private class VCArgs : Arguments() {

        val channel = optionalChannel {
            name = "voicechannel"
            description = "Used for advanced summoning spells"

            validate {
                val channel = this.value
                failIf("This is not a voiceChannel!") {
                    channel != null
                        && channel.type != ChannelType.VOICE
                        && channel.type != ChannelType.STAGE
                }
            }
        }
    }
}
package me.melijn.bot.commands.music

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.SingleConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.editingPaginator
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.suggestStringMap
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.commands.music.MusicExtension.Companion.failIfInvalidTrackIndex
import me.melijn.bot.commands.music.MusicExtension.Companion.tryJoinUser
import me.melijn.bot.database.manager.PlaylistManager
import me.melijn.bot.database.manager.PlaylistTrackManager
import me.melijn.bot.model.PartialUser
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.music.QueuePosition
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsed
import me.melijn.bot.utils.intRanges
import me.melijn.bot.utils.optionalIntRanges
import me.melijn.bot.utils.playlist
import me.melijn.gen.PlaylistData
import me.melijn.kordkommons.utils.StringUtils
import me.melijn.kordkommons.utils.escapeMarkdown
import org.koin.core.component.inject
import kotlin.time.Duration

@KordExtension
class PlaylistCommand : Extension() {

    override val name: String = "playlist"

    val playlistManager by inject<PlaylistManager>()
    val playlistTrackManager by inject<PlaylistTrackManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "playlist"
            description = "Manage playlist things"

            publicSubCommand(::PlaylistListArgs) {
                name = "load"
                description = "Loads all tracks of the playlist into the queue"

                action {
                    val guild = guild!!.asGuild()
                    val playlist = arguments.playlist.parsed
                    val requester = PartialUser.fromKordUser(user.asUser())
                    val tracks = playlistTrackManager.getMelijnTracksInPlaylist(playlist, requester)
                    val trackManager = guild.getTrackManager()
                    val link = trackManager.link
                    if (tryJoinUser(link)) return@action

                    for (track in tracks) trackManager.queue(track, QueuePosition.BOTTOM)

                    respond {
                        content = tr("playlist.load.queued", tracks.size, playlist.name.escapeMarkdown())
                    }
                }
            }

            publicSubCommand(::PlaylistAddArgs) {
                name = "add"
                description = "Adds a track to your playlist"

                action {
                    val guild = guild!!.asGuild()
                    val playlistName = arguments.playlist.parsed
                    val trackManager = guild.getTrackManager()
                    val playingTrack = trackManager.playingTrack

                    // intRanges into tracks conversion
                    val hashSet = HashSet<Int>()
                    val trackIndexes = arguments.trackIndexes.parsed
                    val targetTracks = trackIndexes?.list?.let { ranges ->
                        for (range in ranges)
                            for (i in range) hashSet.add(i - 1)

                        val shouldContainPlayingTrack = hashSet.remove(-1)
                        val tracks = trackManager.getTracksByIndexes(hashSet).toMutableList()
                        if (shouldContainPlayingTrack) playingTrack?.let { tracks.add(0, it) }
                        tracks
                    } ?: buildList { playingTrack?.let { add(it) } }

                    // potentially create non-existant playlist
                    val existingPlaylist = playlistManager.getByNameOrDefault(user.id, playlistName)
                    playlistManager.store(existingPlaylist)

                    val oldTrackCount = playlistTrackManager.getTrackCount(existingPlaylist)

                    // add each track to the existing playlist
                    for (track in targetTracks)
                        playlistTrackManager.newTrack(existingPlaylist, track)

                    if (targetTracks.size == 1) {
                        val targetTrack = targetTracks.first()
                        respond {
                            content = tr(
                                "playlist.add.added", targetTrack.title.escapeMarkdown(),
                                playlistName.escapeMarkdown(), oldTrackCount
                            )
                        }
                    } else {
                        respond {
                            content = tr(
                                "playlist.add.addedMany",
                                targetTracks.size,
                                playlistName.escapeMarkdown(),
                                oldTrackCount,
                                oldTrackCount + targetTracks.size - 1
                            )
                        }
                    }
                }
            }

            publicSubCommand(::PlaylistRemoveArgs) {
                name = "remove"
                description = "Removes tracks from your playlist"

                action {
                    val existingPlaylist = arguments.playlist.parsed
                    val tracks = playlistTrackManager.getTracksInPlaylist(existingPlaylist)
                    val toRemove = tracks.withIndex().filter { (i, _) ->
                        arguments.trackIndexes.parsed.list.any { it.contains(i) }
                    }
                    if (toRemove.size == 1) {
                        val (index, infoPair) = toRemove[0]
                        val (playlistTrackData, trackData) = infoPair
                        playlistTrackManager.delete(playlistTrackData)
                        respond {
                            content = tr(
                                "playlist.remove.removedTrack",
                                index,
                                trackData.url,
                                trackData.title.escapeMarkdown(),
                                existingPlaylist.name.escapeMarkdown()
                            )
                        }
                    } else {
                        playlistTrackManager.deleteAll(toRemove.map { it.value.first })
                        respond {
                            content = tr(
                                "playlist.remove.removedTracks",
                                toRemove.size,
                                existingPlaylist.name.escapeMarkdown()
                            )
                        }
                    }

                    if (toRemove.size == tracks.size) playlistManager.delete(existingPlaylist)
                }
            }

            publicSubCommand {
                name = "playlists"
                description = "Lists all your playlists"

                action {
                    val existingPlaylist = playlistManager.getPlaylistsOfUserWithTrackCount(user.id)

                    var description = "```INI\n# index - public - [name] - tracks - [created]"

                    existingPlaylist.entries.withIndex().forEach { (index, playlistWithCount) ->
                        val (playlist, count) = playlistWithCount
                        description += "\n" + tr(
                            "playlist.list.all.playlistEntry", index, playlist.public, playlist.name, count,
                            java.util.Date.from(playlist.created.toInstant(UtcOffset.ZERO).toJavaInstant())
                        )
                    }
                    description += "```"

                    editingPaginator {
                        val parts = StringUtils.splitMessageWithCodeBlocks(description)
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
                    val existingPlaylist = arguments.playlist.parsed
                    val tracks = playlistTrackManager.getTracksInPlaylist(existingPlaylist)
                    var totalDuration = Duration.ZERO
                    var description = ""

                    tracks.withIndex().forEach { (index, infoPair) ->
                        val (_, trackData) = infoPair
                        totalDuration += trackData.length
                        description += "\n" + tr(
                            "queue.queueEntry", index, trackData.url, trackData.title,
                            trackData.length.formatElapsed()
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

    inner class PlaylistAddArgs : Arguments() {
        val playlist = string {
            name = "playlist"
            description = "New or existing playlist name or existing playlist index"
            autoComplete {
                val playlists = playlistManager.getByIndex1(user.id.value)
                suggestStringMap(playlists.associate { it.name to it.name })
            }
        }
        val trackIndexes = optionalIntRanges {
            name = "trackindex"
            description = "Index of a track in queue, see /queue"
            validate {
                val varmoment = value ?: return@validate
                val trackManager = context.getGuild()!!.asGuild().getTrackManager()
                varmoment.list.forEach {
                    for (i in it) if (i != 0) failIfInvalidTrackIndex(i) { trackManager }
                }
            }
        }
    }

    inner class PlaylistRemoveArgs : Arguments() {
        val playlist: SingleConverter<PlaylistData> = playlist {
            name = "playlist"
            description = "Existing playlist name"

            autoComplete {
                val playlists = playlistManager.getByIndex1(user.id.value)
                suggestStringMap(playlists.associate { it.name to it.name })
            }
        }
        val trackIndexes = intRanges {
            name = "trackindex"
            description = "Index of a track in the playlist, see `/playlist tracks`"
            validate {
                val trackCount = playlistTrackManager.getTrackCount(playlist.parsed)
                value.list.any {
                    val from = it.first
                    val to = it.last
                    failIf(
                        context.tr(
                            "playlist.invalidTrackIndex",
                            from == to, from.toString(), to.toString()
                        )
                    ) {
                        from >= trackCount || from < 0 || to >= trackCount || to < 0
                    }
                }
            }
        }
    }

    inner class PlaylistListArgs : Arguments() {
        val playlist = playlist {
            name = "playlist"
            description = "Existing playlist name"

            autoComplete {
                val playlists = playlistManager.getByIndex1(user.id.value)
                suggestStringMap(playlists.associate { it.name to it.name })
            }
        }
    }
}


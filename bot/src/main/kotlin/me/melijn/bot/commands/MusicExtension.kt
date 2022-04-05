package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.rest.builder.message.create.embed
import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.rest.TrackResponse
import dev.schlaubi.lavakord.rest.loadItem
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.Melijn
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsed
import org.springframework.boot.ansi.AnsiColor
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KordExtension
class MusicExtension : Extension() {
    override val name: String = "music"

    override suspend fun setup() {
        publicSlashCommand {
            name = "nowplaying"
            description = "shows current playing song information"
            check {
                anyGuild()
            }

            action {
                val guildId = guild?.id?.value!!
                val player = Melijn.lavalink.getLink(guildId).player
                val playing = player.playingTrack
                if (playing == null) {
                    respond {
                        content = "There is no track playing"
                    }
                    return@action
                }

                val count = 20
                val progress = ((player.position * count.toDouble()) /
                    playing.length.inWholeMilliseconds.toDouble()).roundToInt()

                val ansiFormat = { color: AnsiColor -> "\u001B[0;${color}m" }
                val blue = ansiFormat(AnsiColor.BLUE)
                val green = ansiFormat(AnsiColor.GREEN)

                val bar = "${blue}${"━".repeat(progress)}${green}${"━".repeat(count-progress)}"
                val status = if (player.paused) "paused" else "playing"


                respond {
                    embed {
                        title = tr("nowplaying.title")
                        description = tr("nowplaying.songLink", playing.title, playing.uri.toString())
                        field {
                            name = tr("nowplaying.progressFieldTitle")
                            value = tr("nowplaying.progressFieldValue", bar,
                                player.positionDuration.formatElapsed(), playing.length.formatElapsed())
                            inline = false
                        }
                        field {
                            name = tr("nowplaying.statusFieldTitle")
                            value = tr("nowplaying.statusFieldValue", status)
                            inline = false
                        }
                        thumbnail {
                            if (playing.uri?.contains("youtu") == true)
                                url = "https://img.youtube.com/vi/${playing.identifier}/hqdefault.jpg"
                        }
                    }
                }
            }
        }

        publicSlashCommand {
            name = "connect"
            description = "bot joins your channel"
            check {
                anyGuild()
            }

            action {
                val guildId = guild?.id?.value!!
                val vc = member?.getVoiceStateOrNull()?.channelId
                if (vc == null) {
                    respond {
                        content = "you are not in a voice channel"
                    }
                    return@action
                }
                val link = Melijn.lavalink.getLink(guildId)
                link.connectAudio(vc.value)
                respond {
                    content = "summoned"
                }
            }
        }
        publicSlashCommand(::PlayArgs) {
            name = "play"
            description = "bot joins your channel and plays moosic"
            check {
                anyGuild()
            }

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                val player = link.player

                val query = arguments.song.parsed
                val search = if (query.startsWith("http")) {
                    query
                } else {
                    "ytsearch:$query"
                }
                if (link.state != Link.State.CONNECTED) {
                    val vc = member?.getVoiceStateOrNull()?.channelId
                    if (vc == null) {
                        respond {
                            content = "you are not in a voice channel"
                        }
                        return@action
                    }
                    link.connectAudio(vc.value)
                }

                val item = link.loadItem(search)
                val track = item.tracks.first()

                when (item.loadType) {
                    TrackResponse.LoadType.TRACK_LOADED -> player.playTrack(track)
                    TrackResponse.LoadType.PLAYLIST_LOADED -> player.playTrack(track)
                    TrackResponse.LoadType.SEARCH_RESULT -> player.playTrack(track)
                    TrackResponse.LoadType.NO_MATCHES -> respond { content = "no matches" }
                    TrackResponse.LoadType.LOAD_FAILED -> respond { content = "Error: ${item.exception?.message}" }
                }

                respond {
                    embed {
                        title = tr("play.title", user.asUser().tag)
                        description = tr(
                            "play.description", track.info.uri, track.info.title, track.info.length.toDuration(
                                DurationUnit.MILLISECONDS
                            ).formatElapsed()
                        )
                    }
                }
            }
        }
        publicSlashCommand {
            name = "stop"
            description = "stops music"

            check {
                anyGuild()
            }

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                link.player.stopTrack()
                respond {
                    content = "stopped"
                }
            }
        }
        publicSlashCommand {
            name = "leave"
            description = "leaves channel"

            check {
                anyGuild()
            }

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                link.destroy()

                respond {
                    content = "left"
                }
            }
        }
        publicSlashCommand {
            name = "pause"
            description = "pause music"

            check {
                anyGuild()
            }

            action {
                val guildId = guild?.id?.value!!
                val link = Melijn.lavalink.getLink(guildId)
                link.player.pause(!link.player.paused)
                respond { content = "paused = ${!link.player.paused}" }
            }
        }
    }

    private class PlayArgs : Arguments() {
        val song = string {
            name = "song"
            description = "songname"
        }
    }
}
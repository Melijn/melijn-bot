package me.melijn.bot.music

import me.melijn.bot.model.TrackSource
import kotlin.time.Duration

class SpotifyTrack(
    override val track: String,
    override val title: String,
    override val author: String?,

    override val url: String,
    override val identifier: String, // spotify trackId

    override val isStream: Boolean,

    override val data: TrackData,
    override val length: Duration,

    override val trackInfoVersion: Byte = 2
) : Track(title, author, url, identifier, isStream, data, length, TrackSource.Spotify, trackInfoVersion) {

    override fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track {
        return dev.schlaubi.lavakord.audio.player.Track(
            trackInfoVersion, track, title, author ?: "", length, identifier ?: "", isStream, !isStream,
            url, sourceType.toString().lowercase(), Duration.ZERO
        )
    }
}
package me.melijn.bot.music

import me.melijn.bot.model.TrackSource
import kotlin.time.Duration

abstract class Track(
    open val title: String,
    open val author: String?,
    open val url: String,
    open val identifier: String?, // for yt: the video id
    open val isStream: Boolean,

    open val data: TrackData?,
    open val length: Duration,

    open val sourceType: TrackSource,
    open val trackInfoVersion: Byte = 2
) {
    abstract val track: String?

    abstract fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track
}


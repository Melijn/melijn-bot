package me.melijn.bot.music

import me.melijn.bot.model.TrackSource
import me.melijn.kordkommons.utils.TimeUtil
import kotlin.time.Duration

@kotlinx.serialization.Serializable
abstract class Track {
    abstract val title: String
    abstract val author: String?
    abstract val url: String
    abstract val identifier: String? // for yt: the video id
    abstract val isStream: Boolean

    abstract val data: TrackData?
    @kotlinx.serialization.Serializable(with = TimeUtil.DurationSerializer::class)
    abstract val length: Duration

    abstract val sourceType: TrackSource
    abstract val track: String?

    abstract val trackInfoVersion: Byte // default 2

    abstract suspend fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track?
    abstract fun getSearchValue(): String
}
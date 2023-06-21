package me.melijn.bot.music

import kotlinx.serialization.Serializable
import me.melijn.bot.model.TrackSource
import me.melijn.kordkommons.utils.TimeUtil
import kotlin.time.Duration

@Serializable
class FetchedTrack(
    override val track: String,
    override var title: String,
    override var author: String?,
    override var url: String,
    override var identifier: String?, // for yt: the video id

    override var isStream: Boolean,

    override var data: TrackData,
    @Serializable(with = TimeUtil.DurationSerializer::class)
    override var length: Duration,

    override var sourceType: TrackSource,
    override var trackInfoVersion: Byte = 2
) : Track() {

    override suspend fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track {
        return dev.schlaubi.lavakord.audio.player.Track(
            trackInfoVersion, track, title, author ?: "", length, identifier ?: "", isStream, !isStream,
            url, sourceType.toString().lowercase(), Duration.ZERO
        )
    }

    override fun getSearchValue(): String {
        return identifier ?: (title + (author?.let { " $it" } ?: ""))
    }

    companion object {
        fun fromLavakordTrackWithData(newTrack: dev.schlaubi.lavakord.audio.player.Track, trackData: TrackData): FetchedTrack {
            newTrack.run {
                return FetchedTrack(
                    track, title, author, uri ?: "", identifier, isStream, trackData,
                    length, TrackSource.bestMatch(source), version
                )
            }
        }
    }
}
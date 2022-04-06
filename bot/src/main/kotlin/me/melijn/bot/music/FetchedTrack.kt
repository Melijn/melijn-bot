package me.melijn.bot.music

import me.melijn.bot.model.TrackSource
import kotlin.time.Duration

class FetchedTrack(
    override val track: String,
    override var title: String,
    override var author: String?,
    override var url: String,
    override var identifier: String?, // for yt: the video id

    override var isStream: Boolean,

    override var data: TrackData,
    override var length: Duration,

    override var sourceType: TrackSource,
    override var trackInfoVersion: Byte = 2
) : Track(title, author, url, identifier, isStream, data, length, sourceType, trackInfoVersion) {

    override fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track {
        return dev.schlaubi.lavakord.audio.player.Track(
            trackInfoVersion, track, title, author ?: "", length, identifier ?: "", isStream, !isStream,
            url, sourceType.toString().lowercase(), Duration.ZERO
        )
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
package me.melijn.bot.music

import dev.arbjerg.lavalink.protocol.v4.TrackInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.melijn.bot.model.TrackSource
import me.melijn.kordkommons.utils.TimeUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import dev.arbjerg.lavalink.protocol.v4.Track as LTrack

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
    override var trackInfoVersion: Byte = 4
) : Track() {

    override suspend fun getLavakordTrack(): LTrack {
        return LTrack(
            track,
            TrackInfo(
                identifier ?: "",
                !isStream,
                author ?: "",
                length.inWholeMilliseconds,
                isStream,
                0,
                title,
                url,
                sourceType.toString().lowercase(),
                null,
                null
            ), Json.decodeFromString("{}")
        )
    }

    override fun getSearchValue(): String {
        return identifier ?: (title + (author?.let { " $it" } ?: ""))
    }

    companion object {
        fun fromLavakordTrackWithData(
            newTrack: LTrack,
            trackData: TrackData
        ): FetchedTrack {
            newTrack.info.run {
                return FetchedTrack(
                    newTrack.encoded, title, author, uri ?: "", identifier, isStream, trackData,
                    length.milliseconds, TrackSource.bestMatch(this.sourceName), 4
                )
            }
        }
    }
}
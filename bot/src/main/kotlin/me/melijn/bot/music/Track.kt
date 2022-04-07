package me.melijn.bot.music

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import me.melijn.bot.model.TrackSource
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

@kotlinx.serialization.Serializable
abstract class Track {
    abstract val title: String
    abstract val author: String?
    abstract val url: String
    abstract val identifier: String? // for yt: the video id
    abstract val isStream: Boolean

    abstract val data: TrackData?
    @kotlinx.serialization.Serializable(with = DurationSerializer::class)
    abstract val length: Duration

    abstract val sourceType: TrackSource
    abstract val track: String?

    abstract val trackInfoVersion: Byte // default 2

    abstract suspend fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track?
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Millis", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration {
        return java.time.Duration.ofMillis(decoder.decodeLong()).toKotlinDuration()
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Duration) {
        encoder.encodeLong(value.inWholeMilliseconds)
    }
}
package me.melijn.bot.music

import me.melijn.bot.Melijn
import me.melijn.bot.model.TrackSource
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration

@kotlinx.serialization.Serializable
class SpotifyTrack(
    override val title: String,
    override val author: String?,

    override val url: String,
    override val identifier: String, // spotify trackId

    override val isStream: Boolean,

    override val data: TrackData,
    @kotlinx.serialization.Serializable(with = DurationSerializer::class)
    override val length: Duration
) : Track() {

    override val trackInfoVersion: Byte = 2
    override val sourceType: TrackSource = TrackSource.Spotify
    override val track: String? = null

    private val trackLoader: TrackLoader by inject(TrackLoader::class.java)

    override suspend fun getLavakordTrack(): dev.schlaubi.lavakord.audio.player.Track? {
        val tracks = trackLoader.search(Melijn.lavalink.nodes.first(), "$title $author", data.requester)
        val fetched = tracks.firstOrNull() ?: return null
        return fetched.getLavakordTrack()
    }
}
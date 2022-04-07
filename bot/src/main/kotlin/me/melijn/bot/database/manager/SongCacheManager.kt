package me.melijn.bot.database.manager

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.melijn.ap.injector.Inject
import me.melijn.bot.music.FetchedTrack
import me.melijn.kordkommons.database.DriverManager
import org.koin.java.KoinJavaComponent
import se.michaelthelin.spotify.model_objects.specification.Track
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified
import java.util.concurrent.TimeUnit

@Inject
class SongCacheManager(private val driverManager: DriverManager) {

    val objectMapper by KoinJavaComponent.inject<ObjectMapper>(ObjectMapper::class.java)

    fun storeFetched(input: String, fetchedTracks: List<FetchedTrack>) {
        val time = if (fetchedTracks.isEmpty()) 1 else 5
        driverManager.setCacheEntry(
            "melijn:songcache:${input}",
            Json.encodeToString(fetchedTracks),
            time,
            TimeUnit.MINUTES,
            compress = true
        )
    }

    /**
     * can be empty if no tracks are cached
     */
    suspend fun getFetched(input: String): List<FetchedTrack> {
        return driverManager.getCacheEntry(
            "melijn:songcache:${input}", 10,
            compress = true
        )?.run {
            Json.decodeFromString<List<FetchedTrack>>(this)
        } ?: emptyList()
    }

    fun storeSpotify(input: String, tracks: List<Track>) {
        val time = if (tracks.isEmpty()) 1 else 5
        driverManager.setCacheEntry(
            "melijn:songcache:spotify:${input}",
            objectMapper.writeValueAsString(tracks),
            time,
            compress = true
        )
    }

    /**
     * can be empty if no tracks are cached
     */
    suspend fun getSpotify(input: String): List<Track> {
        return driverManager.getCacheEntry("melijn:songcache:spotify:${input}", 10, compress = true)?.run {
            objectMapper.readValue<List<Track>>(this)
        } ?: emptyList()
    }

    fun storeSpotifySimplified(input: String, tracks: List<TrackSimplified>) {
        val time = if (tracks.isEmpty()) 1 else 5
        driverManager.setCacheEntry(
            "melijn:songcache:spotifysimplified:${input}",
            objectMapper.writeValueAsString(tracks),
            time,
            compress = true
        )
    }

    /**
     * can be empty if no tracks are cached
     */
    suspend fun getSpotifySimplified(input: String): List<TrackSimplified> {
        return driverManager.getCacheEntry("melijn:songcache:spotifysimplified:${input}", 10, compress = true)?.run {
            objectMapper.readValue<List<TrackSimplified>>(this)
        } ?: emptyList()
    }
}
package me.melijn.bot.web.api

import com.neovisionaries.i18n.CountryCode
import kotlinx.coroutines.future.await
import me.melijn.bot.database.manager.SongCacheManager
import me.melijn.bot.model.PartialUser
import me.melijn.bot.music.SpotifyTrack
import me.melijn.bot.music.Track
import me.melijn.bot.music.TrackData
import me.melijn.gen.Settings
import org.koin.java.KoinJavaComponent.inject
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified
import java.time.Duration
import kotlin.math.min
import kotlin.time.toKotlinDuration


class MySpotifyApi(spotifySettings: Settings.Api.Spotify) {

    private var api: SpotifyApi = SpotifyApi.Builder()
        .setClientId(spotifySettings.clientId)
        .setClientSecret(spotifySettings.password)
        .build()

    private val songCacheManager by inject<SongCacheManager>(SongCacheManager::class.java)

    suspend fun updateSpotifyCredentials() {
        val credentialsRequest = api.clientCredentials().build()
        api.accessToken = credentialsRequest.executeAsync().await().accessToken
    }

    companion object {
        private val spotifyTrackUrl = Regex("https://open\\.spotify\\.com/track/(\\w+)(?:\\?\\S+)?")
        private val spotifyTrackUri = Regex("spotify:track:(\\w+)")
        private val spotifyPlaylistUrl = Regex("https://open\\.spotify\\.com(?:/user/.*)?/playlist/(\\w+)(?:\\?\\S+)?")
        private val spotifyPlaylistUri = Regex("spotify:(?:user:\\S+:)?playlist:(\\w+)")
        private val spotifyAlbumUrl = Regex("https://open\\.spotify\\.com/album/(\\w+)(?:\\?\\S+)?")
        private val spotifyAlbumUri = Regex("spotify:album:(\\w+)")
        private val spotifyArtistUrl = Regex("https://open\\.spotify\\.com/artist/(\\w+)(?:\\?\\S+)?")
        private val spotifyArtistUri = Regex("spotify:artist:(\\w+)")

        private fun getUrl(id: String) = "https://open.spotify.com/track/$id"

        fun se.michaelthelin.spotify.model_objects.specification.Track.toTrack(requester: PartialUser): Track {
            val trackData = TrackData.fromNow(requester, null)
            return SpotifyTrack(
                name, artists.joinToString(", ") { it.name }, getUrl(id), id, false, trackData,
                Duration.ofMillis(durationMs.toLong()).toKotlinDuration()
            )
        }

        fun TrackSimplified.toTrack(requester: PartialUser): Track {
            val trackData = TrackData.fromNow(requester, null)
            return SpotifyTrack(
                name, artists.joinToString(", ") { it.name }, getUrl(id), id, false, trackData,
                Duration.ofMillis(durationMs.toLong()).toKotlinDuration()
            )
        }
    }

    suspend fun getTracksFromSpotifyUrl(
        songArg: String,
        requester: PartialUser
    ): List<Track> {
        val matchesSingleTrack = spotifyTrackUrl.find(songArg)
        val matchesSingleTrackEmbed by lazy { spotifyTrackUri.find(songArg) }

        val matchesPlaylist by lazy { spotifyPlaylistUrl.find(songArg) }
        val matchesPlaylistEmbed by lazy { spotifyPlaylistUri.find(songArg) }

        val matchesAlbum by lazy { spotifyAlbumUrl.find(songArg) }
        val matchesAlbumEmbed by lazy { spotifyAlbumUri.find(songArg) }

        val matchesArtist by lazy { spotifyArtistUrl.find(songArg) }
        val matchesArtistEmbed by lazy { spotifyArtistUri.find(songArg) }

        return when {
            matchesSingleTrack != null -> listOf(requestTrackInfo(matchesSingleTrack, requester))
            matchesSingleTrackEmbed != null -> listOf(requestTrackInfo(matchesSingleTrackEmbed!!, requester))

            matchesPlaylist != null -> requestPlaylistTracksInfo(matchesPlaylist!!, requester)
            matchesPlaylistEmbed != null -> requestPlaylistTracksInfo(matchesPlaylistEmbed!!, requester)

            matchesAlbum != null -> acceptAlbumResults(matchesAlbum!!, requester)
            matchesAlbumEmbed != null -> acceptAlbumResults(matchesAlbumEmbed!!, requester)

            matchesArtist != null -> acceptArtistResults(matchesArtist!!, requester) //
            matchesArtistEmbed != null -> acceptArtistResults(matchesArtistEmbed!!, requester)
            else -> throw IllegalArgumentException("That is not a valid spotify link")
        }
    }

    private suspend fun requestTrackInfo(track: MatchResult, requester: PartialUser): Track {
        val trackId = track.groupValues[1]

        return getTrackById(trackId).toTrack(requester)
    }

    private suspend fun acceptArtistResults(match: MatchResult, requester: PartialUser): List<Track> {
        val id = match.groupValues[1]
        return (songCacheManager.getSpotify(id).takeIf { it.isNotEmpty() } ?: api
            .getArtistsTopTracks(id, CountryCode.US)
            .build()
            .executeAsync()
            .await()
            .toList()
            .also {
                songCacheManager.storeSpotify(id, it)
            })
            .map { it.toTrack(requester) }
    }

    private suspend fun requestPlaylistTracksInfo(match: MatchResult, requester: PartialUser): List<Track> {
        val id = match.groupValues[1]
        val cached = songCacheManager.getSpotify(id)
        if (cached.isNotEmpty()) return cached.map { it.toTrack(requester) }

        val playlistTracks = mutableListOf<PlaylistTrack>()

        var trackTotal = 1000
        var tracksGottenOffset = 0
        while (trackTotal > tracksGottenOffset) {
            val moreTracks = api
                .getPlaylistsItems(id)
                .limit(100)
                .offset(tracksGottenOffset)
                .build()
                .executeAsync()
                .await()

            moreTracks.items[0].track

            tracksGottenOffset += moreTracks.items.size
            trackTotal = min(moreTracks.total, 1000)
            playlistTracks.addAll(moreTracks.items)
        }

        return playlistTracks.asSequence()
            .map { it.track as se.michaelthelin.spotify.model_objects.specification.Track }
            .also {
                songCacheManager.storeSpotify(id, it.toList())
            }
            .map { it.toTrack(requester) }
            .toList()
    }

    private suspend fun acceptAlbumResults(match: MatchResult, trackData: PartialUser): List<Track> {
        val id = match.groupValues[1]
        return (songCacheManager.getSpotifySimplified(id).takeIf { it.isNotEmpty() } ?: api.getAlbumsTracks(id)
            .build()
            .executeAsync()
            .await()
            .items
            .toList()
            .also {
                songCacheManager.storeSpotifySimplified(id, it)
            })
            .map { it.toTrack(trackData) }
    }

    private suspend fun getTrackById(trackId: String): se.michaelthelin.spotify.model_objects.specification.Track {
        return songCacheManager.getSpotify(trackId).firstOrNull() ?: api.getTrack(trackId)
            .build()
            .executeAsync()
            .await().also {
                songCacheManager.storeSpotify(trackId, listOf(it))
            }
    }

    suspend fun searchTrack(search: String): se.michaelthelin.spotify.model_objects.specification.Track? {
        return songCacheManager.getSpotify(search).firstOrNull() ?: api.searchTracks(search)
            .build()
            .executeAsync()
            .await()
            .items
            .firstOrNull()?.also {
                songCacheManager.storeSpotify(search, listOf(it))
            }
    }
}
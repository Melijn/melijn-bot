package me.melijn.bot.web.api

import com.neovisionaries.i18n.CountryCode
import kotlinx.coroutines.future.await
import me.melijn.bot.music.Track
import me.melijn.gen.Settings
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified
import kotlin.math.min


class MySpotifyApi(spotifySettings: Settings.Api.Spotify) {

    private var api: SpotifyApi = SpotifyApi.Builder()
        .setClientId(spotifySettings.clientId)
        .setClientSecret(spotifySettings.password)
        .build()

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
    }

    suspend fun getTracksFromSpotifyUrl(
        songArg: String
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
            matchesSingleTrack != null -> listOf(requestTrackInfo(matchesSingleTrack))
            matchesSingleTrackEmbed != null -> listOf(requestTrackInfo(matchesSingleTrackEmbed!!))

            matchesPlaylist != null -> requestPlaylistTracksInfo(matchesPlaylist!!)
            matchesPlaylistEmbed != null -> requestPlaylistTracksInfo(matchesPlaylistEmbed!!)

            matchesAlbum != null -> acceptAlbumResults(matchesAlbum!!)
            matchesAlbumEmbed != null -> acceptAlbumResults(matchesAlbumEmbed!!)

            matchesArtist != null -> acceptArtistResults(matchesArtist!!)
            matchesArtistEmbed != null -> acceptArtistResults(matchesArtistEmbed!!)
            else -> throw IllegalArgumentException("That is not a valid spotify link")
        }
    }

    private suspend fun requestTrackInfo(track: MatchResult): Track {
        val trackId = track.groupValues[1]

        return getTrackById(trackId).toTrack()
    }

    private fun se.michaelthelin.spotify.model_objects.specification.Track.toTrack(): Track {
        throw Exception("")
//        return Track(name, artists.contentDeepToString(), uri, id, false, null, durationMs.toLong(),
//            TrackSource.Spotify)
    }

    private suspend fun acceptArtistResults(match: MatchResult): List<Track> {
        val id = match.groupValues[1]
        return api
            .getArtistsTopTracks(id, CountryCode.US)
            .build()
            .executeAsync()
            .await()
            .map { it.toTrack() }
    }

    private suspend fun requestPlaylistTracksInfo(match: MatchResult): List<Track> {
        val id = match.groupValues[1]
        val tracks = mutableListOf<Track>()

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
            tracks.addAll(
                moreTracks.items.mapNotNull { playlistTrack ->
                    (playlistTrack.track as se.michaelthelin.spotify.model_objects.specification.Track?)?.toTrack()
                }
            )
        }

        return tracks
    }

    private fun TrackSimplified.toTrack(): Track {
        throw Exception()
//        return Track(name, artists.contentDeepToString(), uri, id, false, null, durationMs.toLong(),
//                TrackSource.Spotify)
    }

    private suspend fun acceptAlbumResults(match: MatchResult): List<Track> {
        val id = match.groupValues[1]
        return api
            .getAlbumsTracks(id)
            .build()
            .executeAsync()
            .await()
            .items
            .map { it.toTrack() }
    }

    suspend fun getTrackById(trackId: String): se.michaelthelin.spotify.model_objects.specification.Track {
        return api
            .getTrack(trackId)
            .build()
            .executeAsync()
            .await()
    }

    suspend fun searchTrack(search: String): se.michaelthelin.spotify.model_objects.specification.Track? {
        return api.searchTracks(search)
            .build()
            .executeAsync()
            .await()
            .items
            .firstOrNull()
    }

}
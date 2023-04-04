package me.melijn.bot.music

import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent
import dev.minn.jda.ktx.coroutines.await
import dev.schlaubi.lavakord.audio.RestNode
import dev.schlaubi.lavakord.rest.TrackResponse
import dev.schlaubi.lavakord.rest.loadItem
import me.melijn.ap.injector.Inject
import me.melijn.bot.Melijn
import me.melijn.bot.commands.DevExtension
import me.melijn.bot.database.manager.SongCacheManager
import me.melijn.bot.model.PartialUser
import me.melijn.bot.utils.Log
import me.melijn.bot.web.api.MySpotifyApi.Companion.toTrack
import me.melijn.bot.web.api.WebManager
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.koin.core.component.inject

@Inject
class TrackLoader : KordExKoinComponent {

    private val songCacheManager by inject<SongCacheManager>()
    private val webManager by inject<WebManager>()
    private val logger by Log

    /**
     * General search methods, all internal results (pre [trackSearchKeep]) are cached automatically for the given [query]
     *
     * @param restNode used for search requests to lavalink server
     * @param query query text, can be song title, http url, yt link or spotify link
     * @param requester requester data to be appended to each result item
     * @param trackSearchKeep limits the results for song title input
     *
     * @return list of tracks
     */
    suspend fun searchTracks(
        restNode: RestNode,
        query: String,
        requester: PartialUser,
        trackSearchKeep: Int = 1
    ): List<Track> {
        val spotifyApi = webManager.spotifyApi
        val isHttpQuery = query.startsWith("http://") || query.startsWith("https://")
        return if (isHttpQuery && (query.contains("open.spotify.com") && spotifyApi != null)) {
            spotifyApi.getTracksFromSpotifyUrl(query, requester)
        } else {
            val search = if (isHttpQuery) query else "ytsearch:$query"
            val item = restNode.loadItem(search)

            val tracks = handleTrackResponse(item, requester, query)
            if (!isHttpQuery) tracks.take(trackSearchKeep)
            else tracks
        }
    }

    /**
     * General search methods, all internal results (pre [trackSearchKeep]) are cached automatically for the given [query]
     *
     * @param restNode used for search requests to lavalink server
     * @param query query text, can be song title, http url, yt link or spotify link
     * @param requester requester data to be appended to each result item
     * @param trackSearchKeep limits the results for song title input
     *
     * @return list of tracks
     */
    suspend fun searchFetchedTracks(
        restNode: RestNode,
        query: String,
        requester: PartialUser,
        trackSearchKeep: Int = 5
    ): List<FetchedTrack> {
        val spotifyApi = webManager.spotifyApi
        val isHttpQuery = query.startsWith("http://") || query.startsWith("https://")
        val search = if (isHttpQuery && (query.contains("open.spotify.com") && spotifyApi != null)) {
            val spotifyTrack = spotifyApi.getTracksFromSpotifyUrl(query, requester)
                .firstOrNull() ?: return emptyList()
            "ytsearch:${spotifyTrack.getSearchValue()}"
        } else {
            if (isHttpQuery) query else "ytsearch:$query"
        }
        val item = restNode.loadItem(search)

        val tracks = handleTrackResponse(item, requester, query)
        return tracks.take(trackSearchKeep)
    }

    /**
     * Specific search method for yt
     *
     * @param restNode used for search requests to lavalink server
     * @param songName song title
     * @param requester requester data to be appended to each result item
     *
     * @return list of fetchedTracks
     */
    suspend fun searchYT(restNode: RestNode, songName: String, requester: PartialUser): List<FetchedTrack> {
        logger.info { "searching $songName" }
        val cached = songCacheManager.getFetched(songName)
        if (cached.isNotEmpty()) return cached
        val item = restNode.loadItem("ytsearch:$songName")

        return handleTrackResponse(item, requester, songName)
    }

    private suspend fun handleTrackResponse(
        item: TrackResponse,
        requester: PartialUser,
        input: String
    ): List<FetchedTrack> {
        val tracks = when (item.loadType) {
            TrackResponse.LoadType.SEARCH_RESULT,
            TrackResponse.LoadType.TRACK_LOADED,
            TrackResponse.LoadType.PLAYLIST_LOADED -> {
                item.tracks.map {
                    val fullTrack = it.toTrack()
                    val trackData = TrackData.fromNow(requester, fullTrack.identifier)
                    FetchedTrack.fromLavakordTrackWithData(fullTrack, trackData)
                }
            }

            TrackResponse.LoadType.NO_MATCHES, TrackResponse.LoadType.LOAD_FAILED -> {
                emptyList()
            }
        }
        if (tracks.isNotEmpty()) foundTracks(input, tracks)
        return tracks
    }

    private fun foundTracks(input: String, tracks: List<FetchedTrack>) {
        songCacheManager.storeFetched(input, tracks)
    }

    suspend fun fetchTrackFromPresence(guild: Guild, target: User): Track? {
        return guild.retrieveMembers(true, listOf(target)).await()
            ?.firstOrNull()
            ?.let {
                val musicFields = it.activities.asSequence().map { activity ->
                    activity.asRichPresence()?.let { it1 -> DevExtension.getMusicFields(it1) }
                }.firstOrNull() ?: return null

                val requester = PartialUser.fromKordUser(target)
                when (musicFields.detectedProvider.fetchMethod) {
                    DevExtension.TrackFetchMethod.SPOTIFY -> {
                        val searchTerm = buildList {
                            add(musicFields.title)
                            musicFields.author.firstOrNull()?.let { author -> add(author) }
                        }.joinToString(" ")
                        webManager.spotifyApi?.searchTrack(searchTerm)?.toTrack(requester)
                    }

                    DevExtension.TrackFetchMethod.YT -> {
                        val authors = musicFields.author.joinToString(",")
                            .replace("-", "")
                        val query = "\"${musicFields.title} ${authors}\""
                        searchYT(Melijn.lavalink.nodes.first(), query, requester).firstOrNull()
                    }
                }
            }
    }
}
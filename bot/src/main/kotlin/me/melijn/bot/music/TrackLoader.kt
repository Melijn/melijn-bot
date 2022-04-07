package me.melijn.bot.music

import dev.schlaubi.lavakord.audio.RestNode
import dev.schlaubi.lavakord.rest.TrackResponse
import dev.schlaubi.lavakord.rest.loadItem
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.SongCacheManager
import me.melijn.bot.model.PartialUser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Inject
class TrackLoader : KoinComponent {

    private val songCacheManager by inject<SongCacheManager>()

    suspend fun search(restNode: RestNode, input: String, requester: PartialUser, ): List<FetchedTrack> {
        val cached = songCacheManager.getFetched(input)
        if (cached.isNotEmpty()) return cached
        val item = restNode.loadItem("ytsearch:$input")

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
                return emptyList()
            }
        }
        foundTracks(input, tracks)
        return tracks
    }

    private fun foundTracks(input: String, tracks: List<FetchedTrack>) {
        songCacheManager.storeFetched(input, tracks)
    }
}
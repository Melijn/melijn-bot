package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.TimeUtil
import me.melijn.gen.PlaylistData
import me.melijn.gen.PlaylistTrackData
import me.melijn.gen.database.manager.AbstractPlaylistTrackManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class PlaylistTrackManager(override val driverManager: DriverManager) : AbstractPlaylistTrackManager(driverManager){

    fun newTrack(playlist: PlaylistData, track: String) {
        store(PlaylistTrackData(
            playlist.userId,
            playlist.created,
            track,
            TimeUtil.now()
        ))
    }

    fun getTracksInPlaylist(playlist: PlaylistData) = getByIndex0(playlist.userId, playlist.created)
        .sortedBy { it.addedTime }
}
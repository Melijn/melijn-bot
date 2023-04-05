package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Playlist
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.utils.TimeUtil
import me.melijn.gen.PlaylistData
import me.melijn.gen.database.manager.AbstractPlaylistManager
import me.melijn.kordkommons.database.DriverManager
import net.dv8tion.jda.api.entities.UserSnowflake
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import java.util.*

@Inject
class PlaylistManager(override val driverManager: DriverManager) : AbstractPlaylistManager(driverManager) {

    fun getByNameOrDefault(userFlake: UserSnowflake, name: String): PlaylistData {
        return getByName(userFlake, name)
            ?: PlaylistData(UUID.randomUUID(), userFlake.idLong, TimeUtil.now(), name, false)
    }

    private fun getByName(userFlake: UserSnowflake, name: String) = getByIndex2(userFlake.idLong, name)
    fun getPlaylistsOfUser(userFlake: UserSnowflake): List<PlaylistData> {
        return getByIndex1(userFlake.idLong).sortedBy { it.created }
    }

    fun getPlaylistsOfUserWithTrackCount(userFlake: UserSnowflake): Map<PlaylistData, Long> {
        return scopedTransaction {
            // :) https://blog.jdriven.com/2020/02/kotlin-exposed-aggregate-functions/
            // :) https://www.w3schools.com/sql/sql_groupby.asp
            Playlist.join(PlaylistTrack, JoinType.LEFT) {
                Playlist.playlistId.eq(PlaylistTrack.playlistId)
            }.slice(
                Playlist.playlistId, Playlist.userId, Playlist.created, Playlist.name, Playlist.public, PlaylistTrack.playlistId.count()
            ).select {
                Playlist.userId.eq(userFlake.idLong)
            }.groupBy(
                Playlist.playlistId, Playlist.userId, Playlist.created, Playlist.name, Playlist.public
            ).associate { row ->
                PlaylistData(
                    row[Playlist.playlistId], row[Playlist.userId], row[Playlist.created], row[Playlist.name], row[Playlist.public]
                ) to row[PlaylistTrack.playlistId.count()]
            }
        }
    }
}
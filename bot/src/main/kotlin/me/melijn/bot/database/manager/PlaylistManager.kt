package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Playlist
import me.melijn.bot.database.model.PlaylistTrack
import me.melijn.bot.utils.TimeUtil
import me.melijn.gen.PlaylistData
import me.melijn.gen.database.manager.AbstractPlaylistManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select

@Inject
class PlaylistManager(override val driverManager: DriverManager) : AbstractPlaylistManager(driverManager) {

    fun getByNameOrDefault(userId: Snowflake, name: String): PlaylistData {
        return getByName(userId, name) ?: PlaylistData(userId.value, TimeUtil.now(), name, false)
    }

    private fun getByName(userId: Snowflake, name: String) = getByIndex1(userId.value, name)
    fun getPlaylistsOfUser(id: Snowflake): List<PlaylistData> {
        return getByIndex0(id.value).sortedBy { it.created }
    }

    fun getPlaylistsOfUserWithTrackCount(id: Snowflake): Map<PlaylistData, Long> {
        return scopedTransaction {
            // :) https://blog.jdriven.com/2020/02/kotlin-exposed-aggregate-functions/
            // :) https://www.w3schools.com/sql/sql_groupby.asp
            Playlist.join(PlaylistTrack, JoinType.INNER) {
                Playlist.userId.eq(PlaylistTrack.playlistOwner) and (Playlist.created).eq(PlaylistTrack.playlistCreated)
            }.slice(
                Playlist.userId, Playlist.created, Playlist.name, Playlist.public, Playlist.name.count()
            ).select {
                Playlist.userId.eq(id.value)
            }.groupBy(
                Playlist.userId, Playlist.created, Playlist.name, Playlist.public
            ).associate { row ->
                PlaylistData(
                    row[Playlist.userId], row[Playlist.created], row[Playlist.name], row[Playlist.public]
                ) to row[Playlist.name.count()]
            }
        }
    }
}
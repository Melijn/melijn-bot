package me.melijn.bot.music

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import me.melijn.bot.model.PartialUser

@kotlinx.serialization.Serializable
data class TrackData(
    var requester: PartialUser,
    var requestedAt: Instant,
    var thumbnailId: String?
) {
    companion object {
        fun fromNow(requester: PartialUser, thumbnailId: String?): TrackData {
            return TrackData(requester, java.time.Instant.now().toKotlinInstant(), thumbnailId)
        }
    }
}
package me.melijn.bot.music

import me.melijn.bot.model.TrackSource

data class Track(
    var title: String,
    var author: String?,
    var url: String,
    var identifier: String?, // for yt: the video id
    var isStream: Boolean,

    var data: Any?,
    var duration: Long,

    var sourceType: TrackSource,
)


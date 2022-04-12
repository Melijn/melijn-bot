package me.melijn.bot.model

import me.melijn.bot.music.TrackType

enum class TrackSource(val trackType: TrackType) {
    YOUTUBE(TrackType.FETCHED),
    SOUNC_CLOUD(TrackType.FETCHED),
    TWITCH(TrackType.FETCHED),
    SPOTIFY(TrackType.SPOTIFY),
    HTTP(TrackType.FETCHED),
    UNKNOWN(TrackType.FETCHED);

    companion object {
        fun bestMatch(value: String): TrackSource {
            val match = values().firstOrNull {
                it.toString().equals(value, true) ||
                    value.contains(it.toString(), true)
            }
            return match ?: UNKNOWN
        }
    }
}
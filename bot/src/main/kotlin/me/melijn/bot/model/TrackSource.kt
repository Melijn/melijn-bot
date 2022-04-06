package me.melijn.bot.model

enum class TrackSource {
    Youtube,
    SoundCloud,
    Twitch,
    Spotify,
    Http,
    Unknown;

    companion object {
        fun bestMatch(value: String): TrackSource {
            val match = values().firstOrNull {
                it.toString().equals(value, true) ||
                    value.contains(it.toString(), true)
            }
            return match ?: Unknown
        }
    }
}
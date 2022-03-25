package me.melijn.bot.model.enums

enum class DiscordSize {
    ORIGINAL,
    X64,
    X128,
    X256,
    X512,
    X1024,
    X2048;

    fun getParam(): String {
        return if (this == ORIGINAL) ""
        else "?size=" + this.toString().drop(1)
    }
}